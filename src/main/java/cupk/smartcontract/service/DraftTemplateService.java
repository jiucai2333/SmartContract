package cupk.smartcontract.service;

import cupk.smartcontract.dto.DraftTemplateAnalysis;
import cupk.smartcontract.dto.DraftTemplateAnalysis.DraftField;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DraftTemplateService {

    private final AiDraftService aiDraftService;

    public DraftTemplateService(AiDraftService aiDraftService) {
        this.aiDraftService = aiDraftService;
    }

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "(?<label>甲方|乙方|委托方|受托方|合同金额|金额|工期|服务期限|交付物|付款节点|付款方式|签约日期|签订日期)"
                    + "\\s*[：:]\\s*(?<placeholder>_{2,}|-{2,}|（\\s*）|\\(\\s*\\)|【\\s*】|\\[\\s*]|<[^>]+>|\\{\\{[^}]+}})");

    private static final List<FieldDefinition> KEY_FIELDS = List.of(
            new FieldDefinition("partyA", "甲方信息", "text", true, List.of("甲方", "委托方")),
            new FieldDefinition("partyB", "乙方信息", "text", true, List.of("乙方", "受托方")),
            new FieldDefinition("duration", "工期 / 服务期限", "text", true, List.of("工期", "服务期限")),
            new FieldDefinition("amount", "合同金额", "number", true, List.of("合同金额", "金额")),
            new FieldDefinition("deliverables", "交付物", "textarea", true, List.of("交付物", "交付清单")),
            new FieldDefinition("paymentNodes", "付款节点", "textarea", true, List.of("付款节点", "付款方式", "支付方式")),
            new FieldDefinition("signDate", "签约日期", "date", false, List.of("签约日期", "签订日期"))
    );

    public DraftTemplateAnalysis analyze(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            throw new IllegalArgumentException("OCR 文本不能为空");
        }
        String markdown = normalizeMarkdown(rawText);
        Map<String, DraftField> fields = new LinkedHashMap<>();

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(markdown);
        while (matcher.find()) {
            FieldDefinition definition = definitionFor(matcher.group("label"));
            if (definition == null) {
                continue;
            }
            fields.putIfAbsent(definition.key(), new DraftField(
                    definition.key(), definition.label(), "", matcher.group("placeholder"),
                    definition.inputType(), definition.required(), matcher.group()));
        }

        for (FieldDefinition definition : KEY_FIELDS) {
            fields.putIfAbsent(definition.key(), new DraftField(
                    definition.key(), definition.label(), "", null,
                    definition.inputType(), definition.required(), "AI 关键字段扫描"));
        }

        String analysisMode = "RULE_FALLBACK";
        String notice = "已将 OCR 结果转换为标准 Markdown，并扫描出需要人工确认的关键字段。";
        try {
            List<DraftField> aiFields = aiDraftService.analyzeDraftFields(markdown);
            for (DraftField aiField : aiFields) {
                if (aiField != null && StringUtils.hasText(aiField.key())) {
                    fields.put(aiField.key(), aiField);
                }
            }
            if (!aiFields.isEmpty()) {
                analysisMode = "QWEN_AI_ENHANCED";
                notice = "已将 OCR 结果转换为标准 Markdown，并由 Qwen 扫描出需要人工确认的关键字段。";
            }
        } catch (Exception ignored) {
            notice = "已将 OCR 结果转换为标准 Markdown。AI 服务暂不可用，当前使用规则扫描结果。";
        }

        return new DraftTemplateAnalysis(
                markdown,
                new ArrayList<>(fields.values()),
                analysisMode,
                notice
        );
    }

    public String fill(String markdown, Map<String, String> values) {
        String filled = normalizeMarkdown(markdown);
        for (DraftField field : analyze(markdown).fields()) {
            String value = values.get(field.key());
            if (!StringUtils.hasText(value) || !StringUtils.hasText(field.placeholder())) {
                continue;
            }
            filled = filled.replaceFirst(Pattern.quote(field.placeholder()), Matcher.quoteReplacement(value.trim()));
        }
        return filled;
    }

    private String normalizeMarkdown(String rawText) {
        String normalized = rawText.replace("\r\n", "\n").replace('\r', '\n').trim();
        normalized = normalized.replaceAll("[ \\t]+\\n", "\n");
        normalized = normalized.replaceAll("\\n{3,}", "\n\n");
        if (!normalized.startsWith("#")) {
            normalized = "# OCR 识别合同模板\n\n" + normalized;
        }
        return normalized + "\n";
    }

    private FieldDefinition definitionFor(String label) {
        for (FieldDefinition definition : KEY_FIELDS) {
            if (definition.aliases().contains(label)) {
                return definition;
            }
        }
        return null;
    }

    private record FieldDefinition(
            String key,
            String label,
            String inputType,
            boolean required,
            List<String> aliases
    ) {
    }
}
