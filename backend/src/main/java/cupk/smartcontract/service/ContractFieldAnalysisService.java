package cupk.smartcontract.service;

import com.fasterxml.jackson.databind.JsonNode;
import cupk.smartcontract.dto.ContractFieldAnalysisRequest;
import cupk.smartcontract.dto.ContractFieldAnalysisVO;
import cupk.smartcontract.dto.ContractFieldAnalysisVO.ContractField;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ContractFieldAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(ContractFieldAnalysisService.class);

    private static final int MAX_TEXT_LENGTH = 30000;
    private static final int MAX_FIELDS = 120;
    private static final String BLOCK_SELECTOR = "tr, p, li, div";
    private static final Pattern GENERIC_PLACEHOLDER = Pattern.compile(
            "_{2,}|＿{2,}|-{3,}|【\\s*】|\\[\\s*]|\\(\\s*(?:填写)?\\s*\\)|（\\s*(?:填写)?\\s*）");
    private static final Pattern FIELD_LABEL = Pattern.compile(
            "([\\p{IsHan}A-Za-z0-9（）()·/\\-]{1,30})\\s*[:：]\\s*$");
    private static final Pattern SIGNATURE_FIELD = Pattern.compile(
            ".*(?:签署区|签名|签字|签章|盖章|法定代表人|授权代表|签署日期|签订日期|负责人（或授权代表）|负责人\\(或授权代表\\)).*");

    private final QwenContractService qwenContractService;

    public ContractFieldAnalysisService(QwenContractService qwenContractService) {
        this.qwenContractService = qwenContractService;
    }

    public ContractFieldAnalysisVO analyze(ContractFieldAnalysisRequest request) {
        String text = resolveText(request);
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("合同正文不能为空");
        }
        Map<String, ContractField> fields = structuredPlaceholderFields(request.html(), text);
        String contractType = inferContractType(text, request.contractType());
        log.info("[field-analysis] 进入分析, plainText长度={}, 规则初始字段数={}, contractType={}",
                text.length(), fields.size(), contractType);
        log.info("[field-analysis] 开始调用千问, plainText长度={}", text.length());
        JsonNode root = qwenContractService.extractRequiredFields(text, contractType);
        JsonNode aiFields = root.path("fields");
        int aiFieldCount = aiFields.isArray() ? aiFields.size() : 0;
        log.info("[field-analysis] 千问返回字段数={}", aiFieldCount);
        MergeStats mergeStats = mergeAiFields(fields, aiFields, text);
        log.info("[field-analysis] 合并后接受字段数={}, 最终字段数={}, analysisMode=QWEN_AI_ENHANCED",
                mergeStats.accepted(), fields.size());
        log.info("[field-analysis] 字段保留统计: AI返回={}, AI接受={}, 规则字段被AI替换={}, 独立AI字段新增={}, key重命名={}, 跳过={}, 最终字段数={}",
                aiFieldCount, mergeStats.accepted(), mergeStats.ruleReplacements(),
                mergeStats.aiAdditions(), mergeStats.keyRenames(), mergeStats.skipped(), fields.size());
        return response(fields, "QWEN_AI_ENHANCED", "已基于当前合同正文识别待填写字段并生成可用建议值。");
    }

    /**
     * 提交审批前的必填字段检查（仅规则识别，不调用 AI，避免阻塞）。
     */
    public List<String> findApprovalBlockingMissing(String html, String contractType) {
        String text = resolveText(new ContractFieldAnalysisRequest(null, null, html, null, contractType));
        if (!StringUtils.hasText(text)) return List.of();
        Map<String, ContractField> fields = structuredPlaceholderFields(html, text);
        return fields.values().stream()
                .filter(field -> "required".equals(field.requiredLevel()))
                .filter(field -> "unfilled".equals(field.status()))
                .map(ContractField::fieldName)
                .distinct()
                .toList();
    }

    private Map<String, ContractField> structuredPlaceholderFields(String html, String text) {
        if (StringUtils.hasText(html)) {
            Map<String, ContractField> htmlFields = htmlPlaceholderFields(html);
            if (!htmlFields.isEmpty()) return htmlFields;
        }
        return textPlaceholderFields(text);
    }

    private Map<String, ContractField> htmlPlaceholderFields(String html) {
        Map<String, ContractField> fields = new LinkedHashMap<>();
        Element body = Jsoup.parseBodyFragment(html).body();
        normalizeBlankUnderlineElements(body);
        Elements blocks = body.select(BLOCK_SELECTOR);
        String role = "";
        int blockIndex = 0;
        for (Element block : blocks) {
            if (fields.size() >= MAX_FIELDS) break;
            if (isSignatureElement(block)) continue;
            if (hasChildBlock(block)) continue;
            String line = normalizeLine(block.text());
            if (!StringUtils.hasText(line)) continue;
            if (isSignatureFieldText(line)) continue;
            String lineRole = detectRole(line);
            if (StringUtils.hasText(lineRole)) role = lineRole;
            addLineFields(fields, line, blockIndex, role, "block");
            blockIndex++;
        }
        return fields;
    }

    private void normalizeBlankUnderlineElements(Element root) {
        for (Element element : root.select("u, .contract-fill-underline, [style]")) {
            if (!isBlankUnderlineElement(element)) continue;
            element.text("____");
        }
    }

    private boolean isBlankUnderlineElement(Element element) {
        String tag = element.tagName().toLowerCase(Locale.ROOT);
        String style = element.attr("style");
        boolean underlined = "u".equals(tag)
                || element.hasClass("contract-fill-underline")
                || style.toLowerCase(Locale.ROOT).contains("text-decoration")
                && style.toLowerCase(Locale.ROOT).contains("underline")
                || style.toLowerCase(Locale.ROOT).contains("border-bottom")
                && !style.toLowerCase(Locale.ROOT).contains("border-bottom: none");
        if (!underlined) return false;
        String compact = element.text().replaceAll("\\s+", "").replaceAll("[.·。．]", "");
        return compact.isBlank();
    }

    private Map<String, ContractField> textPlaceholderFields(String text) {
        Map<String, ContractField> fields = new LinkedHashMap<>();
        String role = "";
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length && fields.size() < MAX_FIELDS; i++) {
            String line = normalizeLine(lines[i]);
            if (!StringUtils.hasText(line)) continue;
            if (isSignatureFieldText(line)) continue;
            String lineRole = detectRole(line);
            if (StringUtils.hasText(lineRole)) role = lineRole;
            addLineFields(fields, line, i, role, "line");
        }
        return fields;
    }

    private void addLineFields(Map<String, ContractField> fields, String line, int blockIndex,
                               String role, String locatorType) {
        Matcher matcher = GENERIC_PLACEHOLDER.matcher(line);
        List<PlaceholderHit> hits = new ArrayList<>();
        while (matcher.find()) {
            hits.add(new PlaceholderHit(matcher.start(), matcher.end(), matcher.group()));
        }
        Map<Integer, DateGroup> dateGroups = dateGroups(line, hits);
        Set<Integer> groupedHits = new java.util.HashSet<>();
        for (DateGroup group : dateGroups.values()) {
            for (int i = group.startIndex(); i <= group.endIndex(); i++) {
                groupedHits.add(i);
            }
        }
        for (int i = 0; i < hits.size() && fields.size() < MAX_FIELDS; i++) {
            DateGroup dateGroup = dateGroups.get(i);
            if (dateGroup != null) {
                String key = uniqueRuleKey(fields,
                        semanticKey(dateGroup.fieldName() + "_" + blockIndex + "_" + (i + 1)), fields.size() + 1);
                fields.put(key, new ContractField(
                        key, dateGroup.fieldName(), "date", inferRequiredLevel(dateGroup.fieldName()),
                        dateGroup.placeholderText(), "", clip(line, 240), 0.76, "unfilled",
                        locatorType, blockIndex, StringUtils.hasText(role) ? role : null, i + 1,
                        clip(line.substring(0, dateGroup.start()), 120),
                        clip(line.substring(dateGroup.end()), 120)));
                i = dateGroup.endIndex();
                continue;
            }
            if (groupedHits.contains(i)) continue;
            PlaceholderHit hit = hits.get(i);
            String before = line.substring(0, hit.start());
            String after = line.substring(hit.end());
            String fieldName = inferFieldName(line, before, role, i, hits.size());
            String key = uniqueRuleKey(fields,
                    semanticKey(fieldName + "_" + blockIndex + "_" + (i + 1)), fields.size() + 1);
            String placeholder = placeholderText(line, before, hit);
            fields.put(key, new ContractField(
                    key, fieldName, inferFieldType(fieldName), inferRequiredLevel(fieldName),
                    placeholder, "", clip(line, 240), 0.72, "unfilled",
                    locatorType, blockIndex, StringUtils.hasText(role) ? role : null, i + 1,
                    clip(before, 120), clip(after, 120)));
        }
    }

    private Map<Integer, DateGroup> dateGroups(String line, List<PlaceholderHit> hits) {
        Map<Integer, DateGroup> groups = new LinkedHashMap<>();
        int groupOrdinal = 0;
        for (int i = 0; i + 2 < hits.size(); i++) {
            PlaceholderHit year = hits.get(i);
            PlaceholderHit month = hits.get(i + 1);
            PlaceholderHit day = hits.get(i + 2);
            String yearSep = line.substring(year.end(), month.start());
            String monthSep = line.substring(month.end(), day.start());
            String dayTail = line.substring(day.end(), Math.min(line.length(), day.end() + 2));
            if (!yearSep.contains("年") || !monthSep.contains("月") || !dayTail.startsWith("日")) {
                continue;
            }
            int end = day.end() + 1;
            String before = line.substring(0, year.start());
            int start = year.start();
            Matcher labelMatcher = FIELD_LABEL.matcher(before);
            if (labelMatcher.find() && !isTimelineLine(line)) {
                start = labelMatcher.start();
            }
            groups.put(i, new DateGroup(i, i + 2, start, end,
                    inferDateGroupFieldName(line, before, groupOrdinal),
                    line.substring(start, end)));
            groupOrdinal++;
            i += 2;
        }
        return groups;
    }

    private MergeStats mergeAiFields(Map<String, ContractField> fields, JsonNode aiFields, String text) {
        if (!aiFields.isArray()) return new MergeStats(0, 0, 0, 0, 0);
        int accepted = 0;
        int skipped = 0;
        int ruleReplacements = 0;
        int aiAdditions = 0;
        int keyRenames = 0;
        Set<String> consumedRuleKeys = new java.util.HashSet<>();
        for (JsonNode node : aiFields) {
            if (accepted >= MAX_FIELDS) break;
            String fieldName = safe(node.path("fieldName").asText(), 80);
            if (!StringUtils.hasText(fieldName)) {
                skipped++;
                continue;
            }
            String source = safe(node.path("sourceText").asText(), 240);
            String placeholder = safeNullable(node.path("placeholderText").asText(), 120);
            if (isSignatureFieldText(fieldName) || isSignatureFieldText(source) || isSignatureFieldText(placeholder)) {
                skipped++;
                continue;
            }
            String baseKey = safe(node.path("fieldKey").asText(), 60);
            if (!baseKey.matches("[A-Za-z][A-Za-z0-9_]{1,59}")) baseKey = semanticKey(fieldName);
            if (!StringUtils.hasText(baseKey)) {
                skipped++;
                continue;
            }
            String suggestion = safe(node.path("suggestedValue").asText(), 300);
            double confidence = Math.max(0, Math.min(1, node.path("confidence").asDouble(0.5)));
            ContractField existing = findExistingField(fields, placeholder, source, consumedRuleKeys);
            if (!StringUtils.hasText(source) && placeholder == null && existing == null) {
                skipped++;
                continue;
            }
            if (existing == null && !hasExplicitPlaceholder(placeholder) && !hasExplicitPlaceholder(source)) {
                skipped++;
                continue;
            }

            if (existing != null) {
                fields.remove(existing.fieldKey());
                consumedRuleKeys.add(existing.fieldKey());
                ruleReplacements++;
            } else {
                aiAdditions++;
            }
            String key = uniqueAiKey(fields, baseKey);
            if (!key.equals(baseKey)) keyRenames++;
            String status = placeholder != null ? "unfilled"
                    : normalizeStatus(node.path("status").asText(), existing);
            String type = normalizeType(node.path("fieldType").asText(), fieldName);
            String level = normalizeLevel(node.path("requiredLevel").asText(), fieldName);
            fields.put(key, new ContractField(
                    key, fieldName, type, level,
                    placeholder != null ? placeholder : existing == null ? null : existing.placeholderText(),
                    suggestion,
                    StringUtils.hasText(source) ? source : existing == null ? "" : existing.sourceText(),
                    confidence,
                    status,
                    existing == null ? null : existing.locatorType(),
                    existing == null ? null : existing.blockIndex(),
                    existing == null ? null : existing.fieldRole(),
                    existing == null ? null : existing.placeholderIndex(),
                    existing == null ? null : existing.anchorBefore(),
                    existing == null ? null : existing.anchorAfter()));
            accepted++;
        }
        return new MergeStats(accepted, skipped, ruleReplacements, aiAdditions, keyRenames);
    }

    private record MergeStats(int accepted, int skipped, int ruleReplacements,
                              int aiAdditions, int keyRenames) {
    }

    private ContractField findExistingField(Map<String, ContractField> fields, String placeholder,
                                            String source, Set<String> consumedRuleKeys) {
        ContractField sourceOnly = null;
        ContractField placeholderOnly = null;
        for (ContractField field : fields.values()) {
            if (consumedRuleKeys.contains(field.fieldKey())) continue;
            if (field.locatorType() == null && field.blockIndex() == null && field.placeholderIndex() == null) continue;
            boolean placeholderMatches = textMatches(placeholder, field.placeholderText());
            boolean sourceMatches = textMatches(source, field.sourceText());
            if (placeholderMatches && sourceMatches) return field;
            if (sourceOnly == null && sourceMatches) sourceOnly = field;
            if (placeholderOnly == null && placeholderMatches) placeholderOnly = field;
        }
        return sourceOnly != null ? sourceOnly : placeholderOnly;
    }

    private String normalizeType(String value, String fieldName) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return Set.of("text", "number", "date", "textarea", "select").contains(normalized)
                ? normalized : inferFieldType(fieldName);
    }

    private String normalizeLevel(String value, String fieldName) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return Set.of("required", "before_submit", "optional").contains(normalized)
                ? normalized : inferRequiredLevel(fieldName);
    }

    private String inferFieldType(String name) {
        if (name.contains("日期") || name.contains("时间")
                || name.endsWith("年") || name.endsWith("月") || name.endsWith("日")) return "date";
        if (name.contains("金额") || name.contains("数量") || name.contains("单价")) return "number";
        if (name.contains("条款") || name.contains("内容") || name.contains("责任")
                || name.contains("方式") || name.contains("说明")) return "textarea";
        return "text";
    }

    private String inferRequiredLevel(String name) {
        return "optional";
    }

    private boolean hasChildBlock(Element block) {
        for (Element child : block.children()) {
            if (Set.of("tr", "p", "li", "div").contains(child.tagName().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean isSignatureElement(Element element) {
        for (Element current = element; current != null; current = current.parent()) {
            String type = current.attr("data-doc-style").toLowerCase(Locale.ROOT);
            if (current.hasClass("signature-block") || current.hasClass("signature-row")
                    || current.hasClass("signature-table") || type.contains("signature")
                    || type.contains("stamp")) {
                return true;
            }
        }
        return false;
    }

    private boolean isSignatureFieldText(String value) {
        return StringUtils.hasText(value) && SIGNATURE_FIELD.matcher(value.replaceAll("\\s+", "")).matches();
    }

    private String normalizeLine(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("[\\t ]+", " ").trim();
    }

    private String detectRole(String line) {
        String compact = line.replaceAll("\\s+", "");
        if (compact.matches("^(甲方|买方|委托方|广告主).*")) return "甲方";
        if (compact.matches("^(乙方|卖方|受托方|服务方|广告服务提供方).*")) return "乙方";
        if (compact.matches("^(丙方).*")) return "丙方";
        return "";
    }

    private String inferFieldName(String line, String before, String role, int placeholderIndex, int tokenCount) {
        String amountName = inferAmountFieldName(line, placeholderIndex);
        if (amountName != null) return amountName;
        String timelineName = inferTimelineFieldName(line, placeholderIndex);
        if (timelineName != null) return timelineName;
        String paymentName = inferPaymentFieldName(line, placeholderIndex);
        if (paymentName != null) return paymentName;
        Matcher labelMatcher = FIELD_LABEL.matcher(before);
        if (labelMatcher.find()) {
            String label = cleanLabel(labelMatcher.group(1));
            if (StringUtils.hasText(role) && isPartyDetailLabel(label)) return role + label;
            return label;
        }
        if (line.contains("人民法院") && placeholderIndex == 0) return "管辖法院所在地";
        if (line.contains("仲裁委员会") && placeholderIndex == 1) return "仲裁委员会所在地";
        if (line.contains("一式") && tokenCount == 3) {
            return List.of("合同份数", "甲方执份数", "乙方执份数").get(placeholderIndex);
        }
        return "待填写字段" + (placeholderIndex + 1);
    }

    private String inferAmountFieldName(String line, int index) {
        if (!(line.contains("合同金额") || line.contains("总金额") || line.contains("价款") || line.contains("金额"))) {
            return null;
        }
        if ((line.contains("大写") || line.contains("小写")) && GENERIC_PLACEHOLDER.matcher(line).results().count() >= 2) {
            return index == 0 ? "合同金额小写" : "合同金额大写";
        }
        return null;
    }

    private String inferTimelineFieldName(String line, int index) {
        if (!isTimelineLine(line)) return null;
        List<String> names = List.of("服务期限起始年", "服务期限起始月", "服务期限起始日",
                "服务期限终止年", "服务期限终止月", "服务期限终止日");
        return index < names.size() ? names.get(index) : null;
    }

    private boolean isTimelineLine(String line) {
        return line.contains("服务期限") || line.contains("合同期限") || line.contains("有效期");
    }

    private String inferDateGroupFieldName(String line, String before, int groupOrdinal) {
        if (isTimelineLine(line)) {
            return groupOrdinal == 0 ? "服务期限起始日期" : "服务期限终止日期";
        }
        Matcher labelMatcher = FIELD_LABEL.matcher(before);
        if (labelMatcher.find()) {
            String label = cleanLabel(labelMatcher.group(1));
            if (label.endsWith("日期") || label.endsWith("时间")) return label;
            return label + "日期";
        }
        if (line.contains("签署") || line.contains("签订")) return "签署日期";
        if (line.contains("生效")) return "生效日期";
        return groupOrdinal == 0 ? "日期" : "日期" + (groupOrdinal + 1);
    }

    private String inferPaymentFieldName(String line, int index) {
        if (!line.contains("付款安排")) return null;
        List<String> names = List.of("首期款金额", "首期款支付期限", "进度款金额",
                "进度款支付年", "进度款支付月", "进度款支付日", "尾款金额", "尾款支付期限");
        return index < names.size() ? names.get(index) : null;
    }

    private String cleanLabel(String label) {
        String cleaned = label == null ? "" : label.replaceAll("^[0-9一二三四五六七八九十]+[.、]\\s*", "").trim();
        if (cleaned.startsWith("甲方") || cleaned.startsWith("买方") || cleaned.startsWith("委托方")) return "甲方";
        if (cleaned.startsWith("乙方") || cleaned.startsWith("卖方") || cleaned.startsWith("受托方")
                || cleaned.startsWith("服务方")) return "乙方";
        if (cleaned.startsWith("丙方")) return "丙方";
        return cleaned;
    }

    private boolean isPartyDetailLabel(String label) {
        return Set.of("住所地", "联系地址", "地址", "联系人", "联系电话", "电话", "身份证号").contains(label);
    }

    private String placeholderText(String line, String before, PlaceholderHit hit) {
        Matcher labelMatcher = FIELD_LABEL.matcher(before);
        if (labelMatcher.find()) {
            return line.substring(labelMatcher.start(), hit.end());
        }
        return hit.value();
    }

    private String inferContractType(String text, String requestedType) {
        String head = text == null ? "" : clip(text.trim(), 200);
        if (head.contains("广告服务") || head.contains("会展服务") || head.contains("咨询服务")) {
            return "ENTERPRISE_SERVICE";
        }
        if (head.contains("采购")) return "PURCHASE";
        if (head.contains("销售")) return "SALES";
        if (head.contains("技术")) return "TECH";
        if (head.contains("劳动") || head.contains("劳务")) return "LABOR";
        return requestedType;
    }

    private String semanticKey(String name) {
        String ascii = name.replaceAll("[^A-Za-z0-9]+", "");
        return ascii.isBlank() ? "field" + Integer.toHexString(name.hashCode()) : ascii;
    }

    private String uniqueRuleKey(Map<String, ContractField> fields, String base, int index) {
        String key = StringUtils.hasText(base) ? base : "field" + index;
        return fields.containsKey(key) ? key + "_" + index : key;
    }

    private String uniqueAiKey(Map<String, ContractField> fields, String base) {
        String root = StringUtils.hasText(base) ? base : "field";
        if (!fields.containsKey(root)) return root;
        int suffix = 2;
        while (fields.containsKey(root + "_" + suffix)) {
            suffix++;
        }
        return root + "_" + suffix;
    }

    private boolean textMatches(String candidate, String existing) {
        if (!StringUtils.hasText(candidate) || !StringUtils.hasText(existing)) return false;
        if (candidate.equals(existing)) return true;
        String left = normalizeForMatch(candidate);
        String right = normalizeForMatch(existing);
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) return false;
        if (left.contains(right) || right.contains(left)) return true;
        String leftCore = removeFillArtifacts(left);
        String rightCore = removeFillArtifacts(right);
        return StringUtils.hasText(leftCore) && StringUtils.hasText(rightCore)
                && (leftCore.contains(rightCore) || rightCore.contains(leftCore));
    }

    private String normalizeForMatch(String value) {
        if (value == null) return "";
        return value.replace('\u00a0', ' ')
                .replace("：", ":")
                .replace("＿", "_")
                .replaceAll("_{2,}", "____")
                .replaceAll("\\s+", "")
                .trim();
    }

    private String removeFillArtifacts(String value) {
        return value == null ? "" : value
                .replace("____", "")
                .replace("_", "")
                .replace(".", "")
                .replace("·", "")
                .replace("。", "")
                .replace("．", "")
                .trim();
    }

    private boolean hasExplicitPlaceholder(String value) {
        return StringUtils.hasText(value) && GENERIC_PLACEHOLDER.matcher(value).find();
    }

    private String normalizeStatus(String value, ContractField existing) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (Set.of("filled", "unfilled", "ignored").contains(normalized)) return normalized;
        return existing == null ? "unfilled" : existing.status();
    }

    private ContractFieldAnalysisVO response(Map<String, ContractField> fields, String mode, String notice) {
        List<ContractField> list = new ArrayList<>(fields.values());
        Map<String, Integer> insertionOrder = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            insertionOrder.put(list.get(i).fieldKey(), i);
        }
        list.sort(Comparator
                .comparingInt((ContractField field) -> field.blockIndex() == null ? Integer.MAX_VALUE : field.blockIndex())
                .thenComparingInt(field -> field.placeholderIndex() == null ? Integer.MAX_VALUE : field.placeholderIndex())
                .thenComparingInt(field -> insertionOrder.getOrDefault(field.fieldKey(), Integer.MAX_VALUE)));
        int requiredMissing = (int) list.stream()
                .filter(field -> "required".equals(field.requiredLevel()))
                .filter(field -> "unfilled".equals(field.status()))
                .count();
        int beforeSubmitMissing = (int) list.stream()
                .filter(field -> "before_submit".equals(field.requiredLevel()))
                .filter(field -> "unfilled".equals(field.status()))
                .count();
        return new ContractFieldAnalysisVO(list, requiredMissing, beforeSubmitMissing, mode, notice);
    }

    private String resolveText(ContractFieldAnalysisRequest request) {
        String text = StringUtils.hasText(request.plainText())
                ? request.plainText()
                : Jsoup.parseBodyFragment(request.html() == null ? "" : request.html()).body().wholeText();
        return clip(normalizeText(text), MAX_TEXT_LENGTH);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ')
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String safe(String value, int max) {
        return value == null ? "" : clip(value.trim(), max);
    }

    private String safeNullable(String value, int max) {
        String result = safe(value, max);
        return result.isBlank() ? null : result;
    }

    private String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record PlaceholderHit(int start, int end, String value) {
    }

    private record DateGroup(int startIndex, int endIndex, int start, int end,
                             String fieldName, String placeholderText) {
    }

}
