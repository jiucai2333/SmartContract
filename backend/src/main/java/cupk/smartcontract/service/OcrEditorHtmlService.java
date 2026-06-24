package cupk.smartcontract.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cupk.smartcontract.dto.OcrPipelineVO;
import org.jsoup.nodes.Entities;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class OcrEditorHtmlService {
    private static final String BODY_STYLE = "font-family:FangSong,仿宋,serif;font-size:14pt;"
            + "line-height:1.5;text-align:left;text-indent:2em;";
    private static final String CLAUSE_STYLE = "font-family:FangSong,仿宋,serif;font-size:14pt;"
            + "line-height:1.5;text-align:left;text-indent:0;";
    private static final String TITLE_STYLE = "font-family:FangSong,仿宋,serif;font-size:18pt;"
            + "line-height:1.5;font-weight:bold;text-align:center;text-indent:0;";
    private static final String SIGNATURE_STYLE = "font-family:FangSong,仿宋,serif;font-size:14pt;"
            + "line-height:1.5;text-align:left;text-indent:0;";
    private static final String SIGNATURE_BLOCK_STYLE = "margin-top:48px;text-indent:0;";
    private static final String SIGNATURE_ROW_STYLE = "display:grid;grid-template-columns:1fr 1fr;"
            + "margin-bottom:20px;line-height:2;text-indent:0;";
    private static final String SIGNATURE_CELL_STYLE = "white-space:pre-wrap;text-indent:0;";
    private static final String TABLE_STYLE = "width:100%;border-collapse:collapse;"
            + "font-family:FangSong,仿宋,serif;font-size:14pt;line-height:1.5;";
    private static final String CELL_STYLE =
            "border:1px solid #000;padding:4px;text-align:left;text-indent:0;";

    private static final Pattern PURE_PAGE_NUMBER = Pattern.compile(
            "^\\s*(?:第\\s*)?[-—–]?\\s*\\d{1,4}\\s*[-—–]?(?:\\s*页)?\\s*$");
    private static final Pattern CLAUSE = Pattern.compile(
            "^\\s*(?:[一二三四五六七八九十百千万]+[、.]|"
                    + "第[一二三四五六七八九十百千万\\d]+条)\\s*\\S.*$");
    private static final Pattern CHINESE_SUB_CLAUSE = Pattern.compile(
            "^\\s*[（(][一二三四五六七八九十百千万]+[）)].+");
    private static final Pattern NUMERIC_SUB_CLAUSE = Pattern.compile(
            "^\\s*(\\d+)[.、．][^\\d].+");
    private static final Pattern MULTI_LEVEL_NUMBERING = Pattern.compile(
            "^\\s*\\d+(?:\\.\\d+){2,}.*");
    private static final Pattern CONTRACT_TITLE = Pattern.compile(
            "^\\s*.{2,40}(?:合同|协议|协议书|确认书|委托书|订单|条款)\\s*$");
    private static final Pattern SIGNATURE = Pattern.compile(
            ".*(?:签字|签名|签章|盖章|法定代表人|授权代表|签署日期).*");
    private static final Pattern PARTY_LINE = Pattern.compile(
            "^\\s*(?:甲方|乙方|丙方|买方|卖方|委托方|受托方|服务方)"
                    + "(?:[（(].{0,30}[）)])?\\s*[:：].{0,40}$");
    private static final Pattern LEFT_SIGNATURE =
            Pattern.compile(".*(?:甲方|买方|委托方).*");
    private static final Pattern RIGHT_SIGNATURE =
            Pattern.compile(".*(?:乙方|卖方|受托方).*");

    private final ObjectMapper objectMapper;

    public OcrEditorHtmlService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Coordinate HTML is retained only as an optional diagnostic preview. */
    public BuildResult build(String ocrBlocksJson) {
        List<String> warnings = new ArrayList<>();
        if (!StringUtils.hasText(ocrBlocksJson)) return new BuildResult(null, null, warnings);
        try {
            OcrPipelineVO document = objectMapper.readValue(ocrBlocksJson, OcrPipelineVO.class);
            String html = renderCoordinateHtml(document, warnings);
            return StringUtils.hasText(html)
                    ? new BuildResult(html, "ocr_coordinates", warnings)
                    : new BuildResult(null, null, warnings);
        } catch (Exception ex) {
            warnings.add("无法生成 OCR 坐标诊断预览: " + ex.getMessage());
            return new BuildResult(null, null, warnings);
        }
    }

    public String renderCoordinateHtml(OcrPipelineVO document) {
        return renderCoordinateHtml(document, new ArrayList<>());
    }

    /** Builds the only editable content representation used by OCR imports. */
    public String buildEditableHtml(String ocrBlocksJson) {
        if (!StringUtils.hasText(ocrBlocksJson)) return null;
        try {
            OcrPipelineVO document = objectMapper.readValue(ocrBlocksJson, OcrPipelineVO.class);
            return renderEditableHtml(document);
        } catch (Exception ex) {
            return null;
        }
    }

    public String buildPlainTextHtml(String text) {
        if (!StringUtils.hasText(text)) return null;
        StringBuilder html = new StringBuilder();
        List<String> lines = normalizePlainContractLines(text);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            BlockKind kind = classifyPlain(line, i == 0);
            if (kind == BlockKind.SIGNATURE && looksLikeDualSignatureText(line)) {
                appendSignatureRow(html, line);
            } else {
                appendText(html, line, kind);
            }
        }
        return html.toString();
    }

    private String renderEditableHtml(OcrPipelineVO document) {
        if (document == null || document.pages() == null || document.pages().isEmpty()) return null;
        List<EditableBlock> all = new ArrayList<>();
        for (OcrPipelineVO.Page page : document.pages().stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(OcrPipelineVO.Page::pageNo)).toList()) {
            if (page.blocks() == null) continue;
            page.blocks().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(block -> toEditableBlock(block, page))
                    .filter(block -> StringUtils.hasText(block.text())
                            && block.kind() != BlockKind.PAGE_NUMBER)
                    .sorted(readingOrder(page))
                    .forEach(all::add);
        }
        if (all.isEmpty()) return null;
        all = applyContextualKinds(all);

        StringBuilder html = new StringBuilder();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < all.size(); i++) {
            EditableBlock block = all.get(i);
            String duplicateKey = cleanText(block.text()).replaceAll("\\s+", "");
            if (!duplicateKey.isEmpty() && !seen.add(duplicateKey)) continue;

            if (block.kind() == BlockKind.TABLE && appendTable(html, block.source().table())) continue;
            if (isDualBankSignatureLine(block.text()) && hasCoreSignatureWithinNext(all, i, 5)) {
                appendSignatureRow(html, block.text());
                continue;
            }
            if (block.kind() == BlockKind.SIGNATURE_LEFT && i + 1 < all.size()) {
                EditableBlock right = all.get(i + 1);
                if (right.kind() == BlockKind.SIGNATURE_RIGHT && sameSignatureRow(block, right)) {
                    appendSignatureLine(html, block.text(), right.text());
                    i++;
                    continue;
                }
            }
            if (isPageContinuation(block) && appendToPreviousParagraph(html, block.text())) {
                continue;
            }
            for (String line : normalizeLines(block.text(), true)) {
                appendText(html, line,
                        block.kind() == BlockKind.TABLE ? BlockKind.BODY : block.kind());
            }
        }
        return html.toString();
    }

    private List<EditableBlock> applyContextualKinds(List<EditableBlock> blocks) {
        List<EditableBlock> result = new ArrayList<>(blocks.size());
        for (int i = 0; i < blocks.size(); i++) {
            EditableBlock block = blocks.get(i);
            if (block.kind() == BlockKind.BODY && isSubClauseHeading(blocks, i)) {
                result.add(new EditableBlock(block.source(), block.text(), BlockKind.SUB_CLAUSE,
                        block.pageNo(), block.order(), block.bbox()));
            } else {
                result.add(block);
            }
        }
        return result;
    }

    private EditableBlock toEditableBlock(OcrPipelineVO.Block block, OcrPipelineVO.Page page) {
        String text = cleanText(block.text());
        String type = normalize(block.blockType());
        int order = block.order();
        Bbox bbox = readBbox(block);
        return new EditableBlock(block, text, classify(block, type, text, bbox, page),
                page.pageNo(), order, bbox);
    }

    private BlockKind classify(OcrPipelineVO.Block block, String type, String text,
                               Bbox bbox, OcrPipelineVO.Page page) {
        if ("table".equals(type)) return BlockKind.TABLE;
        if (isPageNumber(block, type, text, bbox, page)) return BlockKind.PAGE_NUMBER;
        if (List.of("heading", "section_heading", "clause_heading", "article_heading")
                .contains(type) || CLAUSE.matcher(text).matches()) return BlockKind.CLAUSE;
        if (isTitle(block, type, text, bbox, page)) return BlockKind.TITLE;
        if (isOpeningPartyOrFieldLine(type, text, bbox, page)) return BlockKind.PARTY;
        if (isSignatureBlock(type, text, bbox, page)) {
            if (LEFT_SIGNATURE.matcher(text).matches() || isLeftHalf(bbox, page)) {
                return BlockKind.SIGNATURE_LEFT;
            }
            if (RIGHT_SIGNATURE.matcher(text).matches() || isRightHalf(bbox, page)) {
                return BlockKind.SIGNATURE_RIGHT;
            }
            return BlockKind.SIGNATURE;
        }
        return BlockKind.BODY;
    }

    private boolean isSignatureBlock(String type, String text, Bbox bbox,
                                     OcrPipelineVO.Page page) {
        if (List.of("signature", "signature_block", "signature_left", "signature_right",
                "signature_or_party", "stamp").contains(type)) return true;
        if (!SIGNATURE.matcher(text).matches()) return false;
        if (text.length() <= 60) return true;
        return bbox != null && page.height() != null && page.height() > 0
                && bbox.y0() >= page.height() * 0.72;
    }

    private boolean isPageNumber(OcrPipelineVO.Block block, String type, String text,
                                 Bbox bbox, OcrPipelineVO.Page page) {
        if ("page_number".equals(type) || "number".equals(type)) return true;
        if (block.layoutFeatures() != null && block.layoutFeatures().possiblePageNumber()) return true;
        return PURE_PAGE_NUMBER.matcher(text).matches() && bbox != null
                && page.height() != null && page.height() > 0
                && bbox.y0() >= page.height() * 0.88;
    }

    private boolean isTitle(OcrPipelineVO.Block block, String type, String text,
                            Bbox bbox, OcrPipelineVO.Page page) {
        if ("title".equals(type) || "contract_title".equals(type)) return true;
        if (block.layoutFeatures() != null
                && "center_title".equals(normalize(block.layoutFeatures().indentHint()))) return true;
        String align = normalize(block.align());
        String fontSize = normalize(block.fontSizeLevel());
        boolean visualTitle = bbox != null && page.width() != null && page.height() != null
                && text.length() <= 40 && bbox.y0() <= page.height() * 0.25
                && Math.abs(bbox.centerX() - page.width() / 2.0) <= page.width() * 0.1
                && ("center".equals(align)
                || fontSize.contains("large") || fontSize.contains("title"));
        if (visualTitle) return true;
        if (bbox != null && page.height() != null && page.height() > 0
                && bbox.y0() <= page.height() * 0.35
                && CONTRACT_TITLE.matcher(text).matches()) {
            return true;
        }
        return bbox == null && CONTRACT_TITLE.matcher(text).matches();
    }

    private Comparator<EditableBlock> readingOrder(OcrPipelineVO.Page page) {
        return Comparator
                .comparingDouble((EditableBlock block) -> block.bbox() == null
                        ? block.order() * 1000.0 : block.bbox().y0())
                .thenComparingDouble(block -> block.bbox() == null
                        ? block.order() : block.bbox().x0())
                .thenComparingInt(EditableBlock::order);
    }

    private boolean isLeftHalf(Bbox bbox, OcrPipelineVO.Page page) {
        return bbox != null && page.width() != null && bbox.centerX() < page.width() / 2.0;
    }

    private boolean isRightHalf(Bbox bbox, OcrPipelineVO.Page page) {
        return bbox != null && page.width() != null && bbox.centerX() >= page.width() / 2.0;
    }

    private boolean sameSignatureRow(EditableBlock left, EditableBlock right) {
        if (left.pageNo() != right.pageNo()) return false;
        if (left.bbox() == null || right.bbox() == null) return true;
        return Math.abs(left.bbox().centerY() - right.bbox().centerY())
                <= Math.max(left.bbox().height(), right.bbox().height()) * 1.5;
    }

    private boolean isPageContinuation(EditableBlock block) {
        return block.source().layoutFeatures() != null
                && "page_continuation".equals(
                normalize(block.source().layoutFeatures().indentHint()));
    }

    private boolean appendToPreviousParagraph(StringBuilder html, String text) {
        int close = html.lastIndexOf("</p>");
        if (close < 0 || close != html.length() - 4) return false;
        html.insert(close, Entities.escape(text));
        return true;
    }

    private void appendText(StringBuilder html, String text, BlockKind kind) {
        switch (kind) {
            case TITLE -> html.append("<h1 data-doc-style=\"TITLE\" style=\"")
                    .append(TITLE_STYLE).append("\">").append(Entities.escape(text)).append("</h1>");
            case CLAUSE -> html.append("<h2 class=\"clause-title\" ")
                    .append("data-doc-style=\"SECTION_HEADING\" data-indent-chars=\"0\" style=\"")
                    .append(CLAUSE_STYLE).append("\">").append(Entities.escape(text)).append("</h2>");
            case SUB_CLAUSE -> html.append("<h3 class=\"sub-clause-title\" ")
                    .append("data-doc-style=\"SUB_HEADING\" data-indent-chars=\"2\" style=\"")
                    .append(BODY_STYLE).append("\">").append(Entities.escape(text)).append("</h3>");
            case PARTY -> html.append("<p class=\"party-info\" data-doc-style=\"FIELD_LINE\" ")
                    .append("data-indent-chars=\"0\" style=\"").append(CLAUSE_STYLE).append("\">")
                    .append(Entities.escape(text)).append("</p>");
            case SIGNATURE, SIGNATURE_LEFT, SIGNATURE_RIGHT -> appendSingleSignatureRow(html, text);
            default -> html.append("<p data-doc-style=\"CLAUSE_BODY\" data-indent-chars=\"2\" style=\"")
                    .append(BODY_STYLE).append("\">").append(Entities.escape(text)).append("</p>");
        }
    }

    private BlockKind classifyPlain(String text, boolean firstContentLine) {
        if (firstContentLine && CONTRACT_TITLE.matcher(text).matches()) return BlockKind.TITLE;
        if (CLAUSE.matcher(text).matches()) return BlockKind.CLAUSE;
        if (isChineseSubClauseTitle(text)) return BlockKind.SUB_CLAUSE;
        if (SIGNATURE.matcher(text).matches()) return BlockKind.SIGNATURE;
        if (PARTY_LINE.matcher(text).matches() || isPlainFieldLine(text)) return BlockKind.PARTY;
        return BlockKind.BODY;
    }

    private void appendSignatureLine(StringBuilder html, String left, String right) {
        appendSignatureBlockStart(html);
        appendSignatureRowStart(html);
        appendSignatureCell(html, left);
        appendSignatureCell(html, right);
        html.append("</div></div>");
    }

    private void appendSignatureRow(StringBuilder html, String text) {
        SignatureParts parts = splitDualSignatureText(text);
        appendSignatureLine(html, parts.left(), parts.right());
    }

    private void appendSingleSignatureRow(StringBuilder html, String text) {
        appendSignatureBlockStart(html);
        appendSignatureRowStart(html);
        appendSignatureCell(html, text);
        html.append("</div></div>");
    }

    private void appendSignatureBlockStart(StringBuilder html) {
        html.append("<div class=\"signature-block\" data-doc-style=\"SIGNATURE_BLOCK\" ")
                .append("data-indent-chars=\"0\" style=\"").append(SIGNATURE_BLOCK_STYLE)
                .append("\">");
    }

    private void appendSignatureRowStart(StringBuilder html) {
        html.append("<div class=\"signature-row\" style=\"").append(SIGNATURE_ROW_STYLE)
                .append("\">");
    }

    private void appendSignatureCell(StringBuilder html, String text) {
        html.append("<span style=\"").append(SIGNATURE_CELL_STYLE).append("\">")
                .append(Entities.escape(text == null ? "" : text))
                .append("</span>");
    }

    private boolean appendTable(StringBuilder html, Object table) {
        if (table == null) return false;
        JsonNode root = objectMapper.valueToTree(table);
        JsonNode rows = root.isArray() ? root : root.path("rows");
        if (!rows.isArray() || rows.isEmpty()) return false;
        StringBuilder result = new StringBuilder("<table data-doc-style=\"TABLE\" style=\"")
                .append(TABLE_STYLE).append("\"><tbody>");
        for (JsonNode row : rows) {
            JsonNode cells = row.isArray() ? row : row.path("cells");
            if (!cells.isArray() || cells.isEmpty()) return false;
            result.append("<tr>");
            for (JsonNode cell : cells) {
                String value = cell.isValueNode() ? cell.asText() : cell.path("text").asText();
                result.append("<td style=\"").append(CELL_STYLE).append("\">")
                        .append(Entities.escape(cleanText(value))).append("</td>");
            }
            result.append("</tr>");
        }
        html.append(result).append("</tbody></table>");
        return true;
    }

    private String renderCoordinateHtml(OcrPipelineVO document, List<String> warnings) {
        if (document == null || document.pages() == null) return null;
        StringBuilder html = new StringBuilder();
        for (OcrPipelineVO.Page page : document.pages().stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(OcrPipelineVO.Page::pageNo)).toList()) {
            if (page.width() == null || page.height() == null
                    || page.width() <= 0 || page.height() <= 0) {
                warnings.add("第 " + page.pageNo() + " 页缺少页面尺寸，跳过坐标预览");
                continue;
            }
            html.append("<div class=\"ocr-page\" style=\"position:relative;width:")
                    .append(page.width()).append("px;height:").append(page.height()).append("px;\">");
            if (page.blocks() != null) {
                for (OcrPipelineVO.Block block : page.blocks()) {
                    Bbox bbox = readBbox(block);
                    String text = cleanText(block.text());
                    if (bbox == null || !StringUtils.hasText(text)) continue;
                    html.append("<div class=\"ocr-block\" style=\"position:absolute;left:")
                            .append(format(bbox.x0())).append("px;top:")
                            .append(format(bbox.y0())).append("px;width:")
                            .append(format(bbox.width())).append("px;height:")
                            .append(format(bbox.height())).append("px;overflow:hidden;\">")
                            .append(Entities.escape(text)).append("</div>");
                }
            }
            html.append("</div>");
        }
        return html.toString();
    }

    private List<String> normalizeLines(String text, boolean keepPageNumberLikeBody) {
        if (!StringUtils.hasText(text)) return List.of();
        List<String> lines = new ArrayList<>();
        for (String raw : text.replace('\f', '\n').replace("\r", "").split("\\R+")) {
            String cleaned = cleanText(raw);
            if (StringUtils.hasText(cleaned)
                    && (keepPageNumberLikeBody || !PURE_PAGE_NUMBER.matcher(cleaned).matches())) {
                lines.add(cleaned);
            }
        }
        return lines;
    }

    private List<String> normalizePlainContractLines(String text) {
        List<String> rawLines = normalizeLines(text, false);
        if (rawLines.isEmpty()) return rawLines;
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : rawLines) {
            if (current.isEmpty()) {
                current.append(line);
                continue;
            }
            String currentText = current.toString();
            if (startsNewPlainBlock(line)) {
                lines.add(currentText);
                current.setLength(0);
                current.append(line);
            } else if (continuesPlainBlock(currentText, line)) {
                current.append(line);
            } else {
                lines.add(currentText);
                current.setLength(0);
                current.append(line);
            }
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }

    private boolean startsNewPlainBlock(String line) {
        if (!StringUtils.hasText(line)) return false;
        return CLAUSE.matcher(line).matches()
                || NUMERIC_SUB_CLAUSE.matcher(line).matches()
                || PARTY_LINE.matcher(line).matches()
                || isPlainFieldLine(line)
                || SIGNATURE.matcher(line).matches()
                || CONTRACT_TITLE.matcher(line).matches();
    }

    private boolean continuesPlainBlock(String current, String next) {
        if (!StringUtils.hasText(current) || !StringUtils.hasText(next)) return false;
        if (isStandalonePlainBlock(current)) return false;
        if (next.matches("^[，。；：、）)】].*")) return true;
        if (next.matches("^[＿_]{2,}.*")) return true;
        String trimmed = current.trim();
        return !trimmed.matches(".*[。！？；.!?]$")
                || trimmed.endsWith("：") || trimmed.endsWith(":");
    }

    private boolean isStandalonePlainBlock(String text) {
        return CLAUSE.matcher(text).matches()
                || PARTY_LINE.matcher(text).matches()
                || isPlainFieldLine(text)
                || SIGNATURE.matcher(text).matches()
                || CONTRACT_TITLE.matcher(text).matches();
    }

    private boolean isPlainFieldLine(String text) {
        return text != null && text.matches("^\\s*(?:甲方|乙方|丙方|住所地|联系地址|地址|联系人|联系电话|电话|身份证号)\\s*[：:].*");
    }

    private boolean isOpeningPartyOrFieldLine(String type, String text, Bbox bbox,
                                             OcrPipelineVO.Page page) {
        boolean fieldLine = "party_info".equals(type)
                || PARTY_LINE.matcher(text).matches()
                || isPlainFieldLine(text);
        if (!fieldLine) return false;
        if (bbox == null || page.height() == null || page.height() <= 0) return true;
        return bbox.y0() <= page.height() * 0.45;
    }

    private String cleanText(String text) {
        if (text == null) return "";
        return trimEdgeControls(text.replaceFirst("^#{1,6}\\s*", "")
                .replace('\u00A0', ' ')
                .replace('\t', ' '));
    }

    private String trimEdgeControls(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isEdgeControl(text.charAt(start))) start++;
        while (end > start && isEdgeControl(text.charAt(end - 1))) end--;
        return text.substring(start, end);
    }

    private boolean isEdgeControl(char value) {
        return value != ' ' && Character.isWhitespace(value) || Character.isISOControl(value);
    }

    private boolean isSubClauseHeading(List<EditableBlock> blocks, int index) {
        String text = blocks.get(index).text();
        if (isChineseSubClauseTitle(text)) return true;
        java.util.regex.Matcher matcher = NUMERIC_SUB_CLAUSE.matcher(text);
        if (!matcher.matches() || text.length() >= 25 || MULTI_LEVEL_NUMBERING.matcher(text).matches()) {
            return false;
        }
        int number = Integer.parseInt(matcher.group(1));
        for (int i = index + 1; i < blocks.size(); i++) {
            EditableBlock next = blocks.get(i);
            if (next.pageNo() != blocks.get(index).pageNo()) break;
            if (next.kind() != BlockKind.BODY) continue;
            java.util.regex.Matcher nextMatcher = NUMERIC_SUB_CLAUSE.matcher(next.text());
            if (nextMatcher.matches() && !MULTI_LEVEL_NUMBERING.matcher(next.text()).matches()) {
                return Integer.parseInt(nextMatcher.group(1)) == number + 1;
            }
        }
        return false;
    }

    private boolean isChineseSubClauseTitle(String text) {
        return CHINESE_SUB_CLAUSE.matcher(text).matches() && text.length() < 30;
    }

    private boolean hasCoreSignatureWithinNext(List<EditableBlock> blocks, int index, int limit) {
        int checked = 0;
        int pageNo = blocks.get(index).pageNo();
        for (int i = index + 1; i < blocks.size() && checked < limit; i++, checked++) {
            EditableBlock block = blocks.get(i);
            if (block.pageNo() != pageNo) break;
            if (isCoreSignatureText(block.text())) return true;
        }
        return false;
    }

    private boolean isCoreSignatureText(String text) {
        return text != null && text.matches(".*(?:签名|签字|签章|盖章|签署日期|法定代表人|授权代表).*");
    }

    private boolean isDualBankSignatureLine(String text) {
        return secondFieldIndex(text) > 0;
    }

    private SignatureParts splitDualSignatureText(String text) {
        int split = secondFieldIndex(text);
        if (split <= 0) split = secondSignatureFieldIndex(text);
        if (split <= 0) return new SignatureParts(text, "");
        return new SignatureParts(text.substring(0, split).trim(), text.substring(split).trim());
    }

    private int secondFieldIndex(String text) {
        if (!StringUtils.hasText(text)) return -1;
        int result = -1;
        for (String field : List.of("开户银行", "账户名称", "账号", "银行账号")) {
            int first = text.indexOf(field);
            if (first < 0) continue;
            int second = text.indexOf(field, first + field.length());
            if (second > 0 && (result < 0 || second < result)) result = second;
        }
        return result;
    }

    private boolean looksLikeDualSignatureText(String text) {
        return secondFieldIndex(text) > 0 || secondSignatureFieldIndex(text) > 0;
    }

    private int secondSignatureFieldIndex(String text) {
        if (!StringUtils.hasText(text)) return -1;
        int result = -1;
        for (String field : List.of("乙方", "签名", "签字", "签署日期", "盖章",
                "法定代表人", "授权代表")) {
            int first = text.indexOf(field);
            if (first < 0) continue;
            int second = text.indexOf(field, first + field.length());
            if ("乙方".equals(field)) second = first;
            if (second > 0 && (result < 0 || second < result)) result = second;
        }
        return result;
    }

    private Bbox readBbox(OcrPipelineVO.Block block) {
        if (block == null) return null;
        List<Double> bbox = block.bbox();
        if (bbox != null && bbox.size() == 4) {
            return validBbox(bbox.get(0), bbox.get(1), bbox.get(2), bbox.get(3));
        }
        OcrPipelineVO.LayoutFeatures features = block.layoutFeatures();
        return features == null ? null
                : validBbox(features.x0(), features.y0(), features.x1(), features.y1());
    }

    private Bbox validBbox(double x0, double y0, double x1, double y1) {
        if (!Double.isFinite(x0) || !Double.isFinite(y0)
                || !Double.isFinite(x1) || !Double.isFinite(y1)
                || x1 < x0 || y1 < y0) return null;
        return new Bbox(x0, y0, x1, y1);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String format(double value) {
        if (value == Math.rint(value)) return Long.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.2f", value)
                .replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    public record BuildResult(String html, String source, List<String> warnings) {
    }

    private enum BlockKind {
        TITLE, CLAUSE, SUB_CLAUSE, PARTY, BODY, TABLE, SIGNATURE, SIGNATURE_LEFT, SIGNATURE_RIGHT, PAGE_NUMBER
    }

    private record Bbox(double x0, double y0, double x1, double y1) {
        double width() { return x1 - x0; }
        double height() { return y1 - y0; }
        double centerX() { return (x0 + x1) / 2.0; }
        double centerY() { return (y0 + y1) / 2.0; }
    }

    private record EditableBlock(OcrPipelineVO.Block source, String text, BlockKind kind,
                                 int pageNo, int order, Bbox bbox) {
    }

    private record SignatureParts(String left, String right) {
    }

    OcrPipelineVO generateLayoutFeatures(OcrPipelineVO document, List<String> warnings) {
        return new LayoutFeatureGenerator().generate(document, warnings);
    }

    private static final class LayoutFeatureGenerator {    
        private static final double MIN_FIRST_LINE_INDENT_PX = 35.0;
        private static final double MAX_FIRST_LINE_INDENT_PX = 90.0;
        private static final Pattern PURE_NUMBER = Pattern.compile("^\\d+$");
        private static final Pattern NUMBERED_PARAGRAPH = Pattern.compile(
                "^\\s*(?:\\d+[、.]|[（(][一二三四五六七八九十百千万\\d]+[）)]).*");
        private static final Pattern ARTICLE_HEADING = Pattern.compile(
                "^\\s*第[一二三四五六七八九十百千万\\d]+[条章节款段].*");
        private static final Pattern CHINESE_HEADING = Pattern.compile(
                "^\\s*[一二三四五六七八九十百千万]+[、.].*");
    
        private OcrPipelineVO generate(OcrPipelineVO document, List<String> warnings) {
            if (document == null || document.pages() == null) return document;
            List<OcrPipelineVO.Page> pages = document.pages().stream()
                    .map(page -> generatePage(page, warnings))
                    .toList();
            return new OcrPipelineVO(document.fileType(), document.parseSource(),
                    document.approximate(), pages, document.markdown(), document.warnings());
        }
    
        private OcrPipelineVO.Page generatePage(OcrPipelineVO.Page page, List<String> warnings) {
            if (page == null || page.blocks() == null) return page;
            if (page.width() == null || page.height() == null
                    || page.width() <= 0 || page.height() <= 0) {
                warnings.add("Page " + page.pageNo()
                        + " layout_features_skipped reason=missing_page_size_or_bbox");
                return page;
            }
    
            double pageWidth = page.width();
            double pageHeight = page.height();
            Double bodyLeftX = calculateBodyLeftX(page.blocks(), pageWidth, pageHeight);
            List<OcrPipelineVO.Block> ordered = page.blocks().stream()
                    .sorted(Comparator.comparingInt(OcrPipelineVO.Block::order)).toList();
            Map<String, Gaps> gaps = calculateGaps(ordered);
            List<OcrPipelineVO.Block> blocks = new ArrayList<>(page.blocks().size());
            int generated = 0;
            int pageNumbers = 0;
    
            for (OcrPipelineVO.Block block : page.blocks()) {
                Bbox bbox = validBbox(block);
                if (bbox == null) {
                    blocks.add(block);
                    continue;
                }
                OcrPipelineVO.LayoutFeatures features = buildFeatures(
                        block, bbox, pageWidth, pageHeight, bodyLeftX,
                        gaps.getOrDefault(block.blockId(), Gaps.EMPTY));
                blocks.add(copyWithFeatures(block, features));
                generated++;
                if (features.possiblePageNumber()) pageNumbers++;
            }
    
            warnings.add("Page " + page.pageNo() + " layout_features_generated count=" + generated);
            warnings.add("Page " + page.pageNo() + " body_left_x="
                    + (bodyLeftX == null ? "null" : format(bodyLeftX)));
            warnings.add("Page " + page.pageNo() + " possible_page_number count=" + pageNumbers);
            return new OcrPipelineVO.Page(page.pageNo(), page.width(), page.height(), blocks);
        }
    
        private Double calculateBodyLeftX(List<OcrPipelineVO.Block> blocks,
                                          double pageWidth, double pageHeight) {
            double bucketSize = Math.max(4.0, pageWidth * 0.005);
            Map<Long, List<Double>> buckets = new LinkedHashMap<>();
            for (OcrPipelineVO.Block block : blocks) {
                Bbox bbox = validBbox(block);
                if (bbox == null || !isBodyCandidate(block, bbox, pageWidth, pageHeight)) continue;
                long bucket = Math.round(bbox.x0() / bucketSize);
                buckets.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(bbox.x0());
            }
            return buckets.values().stream()
                    .max(Comparator.<List<Double>>comparingInt(List::size)
                            .thenComparingDouble(values -> -median(values)))
                    .map(this::median)
                    .orElse(null);
        }
    
        private boolean isBodyCandidate(OcrPipelineVO.Block block, Bbox bbox,
                                        double pageWidth, double pageHeight) {
            String type = type(block);
            String text = text(block);
            if (!isBodyTextType(type) || isNeverIndentedType(type)
                    || isSignatureText(text) || isPartyInfoText(text) || isClauseHeading(text)) {
                return false;
            }
            if (bbox.y0() > pageHeight * 0.90) return false;
            double blockWidth = bbox.x1() - bbox.x0();
            double centerX = (bbox.x0() + bbox.x1()) / 2.0;
            return !(blockWidth < pageWidth * 0.45
                    && Math.abs(centerX - pageWidth / 2.0) <= pageWidth * 0.08);
        }
    
        private OcrPipelineVO.LayoutFeatures buildFeatures(
                OcrPipelineVO.Block block, Bbox bbox, double pageWidth, double pageHeight,
                Double bodyLeftX, Gaps gaps) {
            double blockWidth = bbox.x1() - bbox.x0();
            double blockHeight = bbox.y1() - bbox.y0();
            double centerX = (bbox.x0() + bbox.x1()) / 2.0;
            double centerY = (bbox.y0() + bbox.y1()) / 2.0;
            Double delta = bodyLeftX == null ? null : bbox.x0() - bodyLeftX;
            boolean centerLike = Math.abs(centerX - pageWidth / 2.0) <= pageWidth * 0.08
                    && blockWidth <= pageWidth * 0.65;
            boolean footerZone = bbox.y1() >= pageHeight * 0.92;
            String text = text(block);
            String type = type(block);
            boolean pageNumber = PURE_NUMBER.matcher(text).matches() && footerZone && centerLike
                    || "number".equals(type) || "page_number".equals(type);
            String indentHint = indentHint(block, bbox.y0(), bbox.y1() - bbox.y0(),
                    pageHeight, delta, centerLike, pageNumber);
    
            return new OcrPipelineVO.LayoutFeatures(
                    bbox.x0(), bbox.y0(), bbox.x1(), bbox.y1(), blockWidth, blockHeight,
                    centerX, centerY, bbox.x0() / pageWidth, bbox.y0() / pageHeight,
                    bbox.x1() / pageWidth, bbox.y1() / pageHeight,
                    blockWidth / pageWidth, blockHeight / pageHeight, centerX / pageWidth,
                    bodyLeftX, delta, delta != null && Math.abs(delta) <= pageWidth * 0.02,
                    centerLike, bbox.x0() > pageWidth * 0.55, bbox.y0() <= pageHeight * 0.08,
                    footerZone, pageNumber, indentHint, gaps.before(), gaps.after());
        }
    
        private String indentHint(OcrPipelineVO.Block block, double y0, double blockHeight,
                                  double pageHeight,
                                  Double delta, boolean centerLike, boolean pageNumber) {
            String type = type(block);
            String text = text(block);
            if (pageNumber) return "footer_number";
            if (isSignatureText(text)) return "signature";
            if ("title".equals(type) || "contract_title".equals(type)
                    || centerLike && y0 <= pageHeight * 0.30 && text.length() <= 40) {
                return "center_title";
            }
            if (isNeverIndentedType(type) || isPartyInfoText(text)) return "none";
            if (ARTICLE_HEADING.matcher(text).matches()) return "article_heading";
            if (CHINESE_HEADING.matcher(text).matches() && text.length() <= 30)
                return "chinese_heading";
            if (isTopContinuation(text, y0, pageHeight)) return "page_continuation";
            if (delta != null && isFirstLineIndent(delta, blockHeight)) {
                return NUMBERED_PARAGRAPH.matcher(text).matches()
                        ? "first_line_numbered" : "first_line";
            }
            return "none";
        }
    
        private boolean isFirstLineIndent(double delta, double blockHeight) {
            if (delta >= MIN_FIRST_LINE_INDENT_PX && delta <= MAX_FIRST_LINE_INDENT_PX) {
                return true;
            }
            double twoCharacterWidth = blockHeight * 2.0;
            double tolerance = Math.max(10.0, blockHeight * 0.35);
            return delta > 0 && Math.abs(delta - twoCharacterWidth) <= tolerance;
        }
    
        private boolean isTopContinuation(String text, double y0, double pageHeight) {
            return y0 <= pageHeight * 0.12
                    && !NUMBERED_PARAGRAPH.matcher(text).matches()
                    && !isClauseHeading(text) && !isSignatureText(text);
        }
    
        private boolean isClauseHeading(String text) {
            return ARTICLE_HEADING.matcher(text).matches()
                    || CHINESE_HEADING.matcher(text).matches() && text.length() <= 30;
        }
    
        private boolean isNeverIndentedType(String type) {
            return List.of("title", "contract_title", "heading", "clause_heading",
                    "party_info", "signature", "signature_or_party", "stamp",
                    "table", "page_number", "number", "footer").contains(type);
        }
    
        private boolean isBodyTextType(String type) {
            return type.isEmpty() || List.of("text", "paragraph", "body", "unknown").contains(type);
        }
    
        private boolean isPartyInfoText(String text) {
            return text.matches("^\\s*(买方|卖方|甲方|乙方|委托方|受托方)[（(]?.{0,12}[）)]?\\s*[:：]?.*");
        }
    
        private boolean isSignatureText(String text) {
            return text.matches(".*(签名|签字|签章|盖章|签署日期|法定代表人|授权代表).*")
                    || text.matches("^\\s*(甲方|乙方|买方|卖方|委托方|受托方)\\s*[:：]?\\s*$");
        }
    
        private Map<String, Gaps> calculateGaps(List<OcrPipelineVO.Block> ordered) {
            Map<String, Gaps> result = new LinkedHashMap<>();
            for (int i = 0; i < ordered.size(); i++) {
                Bbox current = validBbox(ordered.get(i));
                if (current == null) continue;
                Double before = adjacentGap(ordered, i, -1, current.y0(), true);
                Double after = adjacentGap(ordered, i, 1, current.y1(), false);
                result.put(ordered.get(i).blockId(), new Gaps(before, after));
            }
            return result;
        }
    
        private Double adjacentGap(List<OcrPipelineVO.Block> ordered, int index, int direction,
                                   double edge, boolean before) {
            int adjacentIndex = index + direction;
            if (adjacentIndex < 0 || adjacentIndex >= ordered.size()) return null;
            Bbox adjacent = validBbox(ordered.get(adjacentIndex));
            if (adjacent == null) return null;
            return before ? edge - adjacent.y1() : adjacent.y0() - edge;
        }
    
        private OcrPipelineVO.Block copyWithFeatures(
                OcrPipelineVO.Block block, OcrPipelineVO.LayoutFeatures features) {
            return new OcrPipelineVO.Block(
                    block.blockId(), block.blockType(), block.text(), block.bbox(),
                    block.bboxSource(), block.align(), block.fontSizeLevel(),
                    block.confidence(), block.order(), block.source(),
                    block.approximate(), block.table(), features);
        }
    
        private Bbox validBbox(OcrPipelineVO.Block block) {
            if (block == null || block.bbox() == null || block.bbox().size() != 4) return null;
            double x0 = block.bbox().get(0);
            double y0 = block.bbox().get(1);
            double x1 = block.bbox().get(2);
            double y1 = block.bbox().get(3);
            if (!Double.isFinite(x0) || !Double.isFinite(y0)
                    || !Double.isFinite(x1) || !Double.isFinite(y1)
                    || x1 < x0 || y1 < y0) return null;
            return new Bbox(x0, y0, x1, y1);
        }
    
        private double median(List<Double> values) {
            List<Double> sorted = values.stream().sorted().toList();
            int middle = sorted.size() / 2;
            return sorted.size() % 2 == 0
                    ? (sorted.get(middle - 1) + sorted.get(middle)) / 2.0
                    : sorted.get(middle);
        }
    
        private String type(OcrPipelineVO.Block block) {
            return block == null || block.blockType() == null
                    ? "" : block.blockType().trim().toLowerCase(Locale.ROOT);
        }
    
        private String text(OcrPipelineVO.Block block) {
            return block == null || block.text() == null ? "" : block.text().trim();
        }
    
        private String format(double value) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
    
        private record Bbox(double x0, double y0, double x1, double y1) {}
        private record Gaps(Double before, Double after) {
            private static final Gaps EMPTY = new Gaps(null, null);
        }
    
    }
}
