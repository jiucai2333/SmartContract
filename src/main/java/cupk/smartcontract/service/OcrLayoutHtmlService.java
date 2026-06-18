package cupk.smartcontract.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cupk.smartcontract.dto.OcrDocumentVO;
import cupk.smartcontract.dto.QwenLayoutVO;
import org.jsoup.nodes.Entities;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OcrLayoutHtmlService {

    private final ObjectMapper objectMapper;

    public OcrLayoutHtmlService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public BuildResult build(String ocrBlocksJson, String qwenLayoutJson) {
        List<String> warnings = new ArrayList<>();
        if (!StringUtils.hasText(ocrBlocksJson)) {
            return new BuildResult(null, null, warnings);
        }
        try {
            OcrDocumentVO document = objectMapper.readValue(ocrBlocksJson, OcrDocumentVO.class);
            Map<String, QwenLayoutVO.Block> qwenBlocks = readQwenBlocks(qwenLayoutJson, warnings);
            String html = renderCoordinateHtml(document, qwenBlocks, warnings);
            return StringUtils.hasText(html)
                    ? new BuildResult(html, qwenBlocks.isEmpty() ? "ocr_coordinates" : "qwen_layout", warnings)
                    : new BuildResult(null, null, warnings);
        } catch (Exception ex) {
            warnings.add("OCR coordinate HTML could not be rendered: " + ex.getMessage());
            return new BuildResult(null, null, warnings);
        }
    }

    public String renderCoordinateHtml(OcrDocumentVO document) {
        return renderCoordinateHtml(document, Map.of(), new ArrayList<>());
    }

    public String buildEditableHtml(String ocrBlocksJson, String qwenLayoutJson) {
        if (!StringUtils.hasText(ocrBlocksJson)) return null;
        try {
            OcrDocumentVO document = objectMapper.readValue(ocrBlocksJson, OcrDocumentVO.class);
            Map<String, QwenLayoutVO.Block> qwenBlocks =
                    readQwenBlocks(qwenLayoutJson, new ArrayList<>());
            return renderEditableHtml(document, qwenBlocks);
        } catch (Exception ex) {
            return null;
        }
    }

    public String buildPlainTextHtml(String text) {
        if (!StringUtils.hasText(text)) return null;
        StringBuilder html = new StringBuilder();
        for (String line : text.replace("\r", "").replace('\f', '\n').split("\n")) {
            String cleaned = cleanText(line);
            if (!StringUtils.hasText(cleaned)) continue;
            appendSemanticText(html, cleaned, "", null, "");
        }
        return html.toString();
    }

    private String renderCoordinateHtml(OcrDocumentVO document,
                                        Map<String, QwenLayoutVO.Block> qwenBlocks,
                                        List<String> warnings) {
        if (document == null || document.pages() == null || document.pages().isEmpty()) return null;
        StringBuilder html = new StringBuilder();
        document.pages().stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(OcrDocumentVO.Page::pageNo))
                .forEach(page -> appendPage(html, page, qwenBlocks, warnings));
        return html.toString();
    }

    private void appendPage(StringBuilder html, OcrDocumentVO.Page page,
                            Map<String, QwenLayoutVO.Block> qwenBlocks,
                            List<String> warnings) {
        if (page.width() == null || page.height() == null
                || page.width() <= 0 || page.height() <= 0) {
            warnings.add("Page " + page.pageNo() + " coordinate_html_skipped reason=missing_page_size");
            return;
        }
        html.append("<div class=\"ocr-page\" data-page-no=\"")
                .append(page.pageNo())
                .append("\" style=\"position:relative;width:")
                .append(page.width()).append("px;height:")
                .append(page.height())
                .append("px;overflow:hidden;box-sizing:border-box;\">");

        if (page.blocks() != null) {
            page.blocks().stream()
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparingInt(OcrDocumentVO.Block::order))
                    .forEach(block -> appendBlock(html, block, qwenBlocks.get(block.blockId()),
                            warnings, page.pageNo()));
        }
        html.append("</div>");
    }

    private void appendBlock(StringBuilder html, OcrDocumentVO.Block block,
                             QwenLayoutVO.Block qwenBlock,
                             List<String> warnings, int pageNo) {
        String text = cleanText(qwenBlock != null && StringUtils.hasText(qwenBlock.normalizedText())
                ? qwenBlock.normalizedText() : block.text());
        if (!StringUtils.hasText(text)) return;
        Bbox bbox = readBbox(block);
        if (bbox == null) {
            warnings.add("Page " + pageNo + " block " + block.blockId()
                    + " coordinate_html_fallback reason=missing_bbox");
            html.append("<div class=\"ocr-block ocr-block-fallback\" data-fallback=\"missing-bbox\"")
                    .append(dataAttributes(block, qwenBlock))
                    .append(" style=\"position:relative;display:block;\">")
                    .append(Entities.escape(text))
                    .append("</div>");
            return;
        }

        html.append("<div class=\"ocr-block\"")
                .append(dataAttributes(block, qwenBlock))
                .append(" style=\"position:absolute;left:")
                .append(format(bbox.x0())).append("px;top:")
                .append(format(bbox.y0())).append("px;width:")
                .append(format(bbox.width())).append("px;height:")
                .append(format(bbox.height()))
                .append("px;overflow:hidden;box-sizing:border-box;white-space:pre-wrap;\">")
                .append(Entities.escape(text))
                .append("</div>");
    }

    private String dataAttributes(OcrDocumentVO.Block block, QwenLayoutVO.Block qwenBlock) {
        StringBuilder attributes = new StringBuilder();
        if (StringUtils.hasText(block.blockId())) {
            attributes.append(" data-block-id=\"")
                    .append(Entities.escape(block.blockId())).append('"');
        }
        String blockType = qwenBlock != null && StringUtils.hasText(qwenBlock.blockType())
                ? qwenBlock.blockType() : block.blockType();
        if (StringUtils.hasText(blockType)) {
            attributes.append(" data-block-type=\"")
                    .append(Entities.escape(blockType)).append('"');
        }
        String align = qwenBlock != null ? qwenBlock.align() : block.align();
        if (StringUtils.hasText(align)) {
            attributes.append(" data-align=\"").append(Entities.escape(align)).append('"');
        }
        String fontSizeLevel = qwenBlock != null ? qwenBlock.fontSizeLevel() : block.fontSizeLevel();
        if (StringUtils.hasText(fontSizeLevel)) {
            attributes.append(" data-font-size-level=\"")
                    .append(Entities.escape(fontSizeLevel)).append('"');
        }
        if (block.confidence() != null) {
            attributes.append(" data-confidence=\"")
                    .append(format(block.confidence())).append('"');
        }
        if (StringUtils.hasText(block.bboxSource())) {
            attributes.append(" data-bbox-source=\"")
                    .append(Entities.escape(block.bboxSource())).append('"');
        }
        OcrDocumentVO.LayoutFeatures features = block.layoutFeatures();
        if (features != null && StringUtils.hasText(features.indentHint())) {
            String hint = features.indentHint().toLowerCase(Locale.ROOT);
            attributes.append(" data-indent=\"").append(Entities.escape(hint)).append('"');
            if (hint.contains("first_line")) {
                attributes.append(" data-text-indent=\"2em\"");
            }
        }
        return attributes.toString();
    }

    private String renderEditableHtml(OcrDocumentVO document,
                                      Map<String, QwenLayoutVO.Block> qwenBlocks) {
        if (document == null || document.pages() == null || document.pages().isEmpty()) return null;
        StringBuilder html = new StringBuilder();
        document.pages().stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(OcrDocumentVO.Page::pageNo))
                .forEach(page -> appendEditablePage(html, page, qwenBlocks));
        return html.toString();
    }

    private void appendEditablePage(StringBuilder html, OcrDocumentVO.Page page,
                                    Map<String, QwenLayoutVO.Block> qwenBlocks) {
        if (page.blocks() == null) return;
        List<EditableBlock> blocks = page.blocks().stream()
                .filter(java.util.Objects::nonNull)
                .map(block -> toEditableBlock(block, qwenBlocks.get(block.blockId())))
                .filter(block -> StringUtils.hasText(block.text()))
                .filter(block -> !"page_number".equals(block.type()))
                .sorted(Comparator.comparing(EditableBlock::footer)
                        .thenComparingInt(EditableBlock::order))
                .toList();
        if (blocks.isEmpty()) return;
        html.append("<div class=\"document-page\" data-page-no=\"")
                .append(page.pageNo()).append("\">");
        blocks.forEach(block -> appendEditableBlock(html, block));
        html.append("</div>");
    }

    private EditableBlock toEditableBlock(OcrDocumentVO.Block block, QwenLayoutVO.Block qwenBlock) {
        String text = cleanText(qwenBlock != null && StringUtils.hasText(qwenBlock.normalizedText())
                ? qwenBlock.normalizedText() : block.text());
        String type = normalize(qwenBlock != null && StringUtils.hasText(qwenBlock.blockType())
                ? qwenBlock.blockType() : block.blockType());
        String align = normalizeAlign(qwenBlock != null ? qwenBlock.align() : block.align());
        String fontSizeLevel = normalize(qwenBlock != null
                ? qwenBlock.fontSizeLevel() : block.fontSizeLevel());
        int order = qwenBlock != null && qwenBlock.order() > 0 ? qwenBlock.order() : block.order();
        boolean footer = "footer".equals(type)
                || block.layoutFeatures() != null && block.layoutFeatures().footerZone();
        return new EditableBlock(block, text, type, align, fontSizeLevel, order, footer);
    }

    private void appendEditableBlock(StringBuilder html, EditableBlock block) {
        if ("table".equals(block.type()) && appendTable(html, block.source().table())) return;
        for (String line : block.text().replace('\f', '\n').split("\\R+")) {
            String text = cleanText(line);
            if (StringUtils.hasText(text)) {
                appendSemanticText(html, text, block.type(), block, block.fontSizeLevel());
            }
        }
    }

    private void appendSemanticText(StringBuilder html, String text, String type,
                                    EditableBlock block, String fontSizeLevel) {
        boolean contractTitle = "title".equals(type);
        boolean clauseTitle = "heading".equals(type) || isClauseTitle(text);
        boolean signature = "signature".equals(type) || "stamp".equals(type)
                || isSignatureLine(text);
        String tag = contractTitle ? "h1"
                : clauseTitle ? (isMinorHeading(fontSizeLevel) ? "h3" : "p")
                : signature ? "div" : "p";
        html.append('<').append(tag);
        if (clauseTitle && "p".equals(tag)) {
            html.append(" class=\"clause-title\"");
        } else if (signature) {
            html.append(" class=\"signature-block\"");
        }
        String style = semanticStyle(block, contractTitle, clauseTitle, signature);
        if (StringUtils.hasText(style)) html.append(" style=\"").append(style).append('"');
        html.append('>').append(Entities.escape(text))
                .append("</").append(tag).append('>');
    }

    private boolean appendTable(StringBuilder html, Object table) {
        if (table == null) return false;
        JsonNode root = objectMapper.valueToTree(table);
        JsonNode rows = root.isArray() ? root : root.path("rows");
        if (!rows.isArray() || rows.isEmpty()) return false;
        StringBuilder tableHtml = new StringBuilder("<table>");
        for (JsonNode row : rows) {
            JsonNode cells = row.isArray() ? row : row.path("cells");
            if (!cells.isArray() || cells.isEmpty()) return false;
            tableHtml.append("<tr>");
            for (JsonNode cell : cells) {
                String value = cell.isValueNode() ? cell.asText() : cell.path("text").asText();
                tableHtml.append("<td>").append(Entities.escape(value)).append("</td>");
            }
            tableHtml.append("</tr>");
        }
        html.append(tableHtml).append("</table>");
        return true;
    }

    private String semanticStyle(EditableBlock block, boolean title,
                                 boolean clauseTitle, boolean signature) {
        Map<String, String> styles = new LinkedHashMap<>();
        if (title) {
            styles.put("text-align", "center");
        } else if (block != null && StringUtils.hasText(block.align())) {
            styles.put("text-align", block.align());
        }
        if (!title && !clauseTitle && !signature
                && (block == null || shouldIndent(block))) {
            styles.put("text-indent", "2em");
        }
        return styles.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(";"));
    }

    private boolean shouldIndent(EditableBlock block) {
        if (!block.type().isEmpty() && !"paragraph".equals(block.type())
                && !"body".equals(block.type()) && !"text".equals(block.type())) return false;
        OcrDocumentVO.LayoutFeatures features = block.source().layoutFeatures();
        if (features == null) return true;
        String hint = normalize(features.indentHint());
        // first_line 或 first_line_indent 明确表示首行缩进
        if (hint.contains("first_line")) return true;
        // 标题、编号、签署区、页脚不缩进
        if (hint.contains("none") || hint.contains("no_indent")
                || hint.contains("signature") || hint.contains("title")
                || hint.contains("heading") || hint.contains("article_heading")
                || hint.contains("chinese_heading") || hint.contains("list_item")
                || hint.contains("footer")) return false;
        // 未知类型（含 "unknown"）默认缩进（中文合同正文通常有首行缩进）
        return true;
    }

    private boolean isClauseTitle(String text) {
        return text.matches("^\\s*[一二三四五六七八九十百]+[、.]\\s*\\S.{0,40}$")
                || text.matches("^\\s*第[一二三四五六七八九十百0-9]+条\\s*\\S?.{0,40}$");
    }

    private boolean isSignatureLine(String text) {
        if (text.length() > 80) return false;
        return text.matches(".*(签字|签名|盖章|法定代表人|签署日期|年\\s*月\\s*日).*")
                || text.matches("^\\s*(甲方|乙方)\\s*[：:].*");
    }

    private boolean isMinorHeading(String fontSizeLevel) {
        if (fontSizeLevel == null) return false;
        return fontSizeLevel.contains("small") || fontSizeLevel.contains("3")
                || fontSizeLevel.contains("minor");
    }

    private String normalizeAlign(String align) {
        String normalized = normalize(align);
        return switch (normalized) {
            case "center", "right", "left" -> normalized;
            default -> null;
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, QwenLayoutVO.Block> readQwenBlocks(String json, List<String> warnings) {
        if (!StringUtils.hasText(json)) return Map.of();
        try {
            QwenLayoutVO layout = objectMapper.readValue(json, QwenLayoutVO.class);
            if (layout.blocks() == null) return Map.of();
            return layout.blocks().stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(block -> StringUtils.hasText(block.blockId()))
                    .collect(Collectors.toMap(QwenLayoutVO.Block::blockId, Function.identity(),
                            (first, ignored) -> first));
        } catch (Exception ex) {
            warnings.add("Qwen layout JSON could not be applied: " + ex.getMessage());
            return Map.of();
        }
    }

    private Bbox readBbox(OcrDocumentVO.Block block) {
        List<Double> bbox = block.bbox();
        if (bbox != null && bbox.size() == 4) {
            Bbox value = validBbox(bbox.get(0), bbox.get(1), bbox.get(2), bbox.get(3));
            if (value != null) return value;
        }
        OcrDocumentVO.LayoutFeatures features = block.layoutFeatures();
        return features == null ? null
                : validBbox(features.x0(), features.y0(), features.x1(), features.y1());
    }

    private Bbox validBbox(double x0, double y0, double x1, double y1) {
        if (!Double.isFinite(x0) || !Double.isFinite(y0)
                || !Double.isFinite(x1) || !Double.isFinite(y1)
                || x1 < x0 || y1 < y0) {
            return null;
        }
        return new Bbox(x0, y0, x1, y1);
    }

    private String cleanText(String text) {
        if (text == null) return "";
        return text.replaceFirst("^#{1,6}\\s*", "")
                .replace('\f', ' ')
                .replace("\r", "")
                .trim();
    }

    private String format(double value) {
        if (value == Math.rint(value)) return Long.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.2f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    public record BuildResult(String html, String source, List<String> warnings) {
    }

    private record Bbox(double x0, double y0, double x1, double y1) {
        double width() {
            return x1 - x0;
        }

        double height() {
            return y1 - y0;
        }
    }

    private record EditableBlock(OcrDocumentVO.Block source, String text, String type,
                                 String align, String fontSizeLevel, int order, boolean footer) {
    }
}
