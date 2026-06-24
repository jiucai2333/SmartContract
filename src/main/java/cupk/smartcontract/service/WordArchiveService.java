package cupk.smartcontract.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Service
public class WordArchiveService {

    /** 2em 对应的 Word twips 值（1em ≈ 240 twips，2em = 480 twips） */
    private static final int INDENT_2EM_TWIPS = 480;

    public byte[] toDocx(String html) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Element body = Jsoup.parseBodyFragment(html == null ? "" : html).body();
            configureDocument(document);
            for (Node node : body.childNodes()) {
                appendNode(document, node);
            }
            document.write(output);
            return output.toByteArray();
        }
    }

    private void appendNode(XWPFDocument document, Node node) {
        if (node instanceof TextNode textNode) {
            if (!textNode.text().isBlank()) appendParagraph(document, textNode.text(), null, null, null);
            return;
        }
        if (!(node instanceof Element element)) return;
        if (element.hasClass("ocr-page")) {
            List<Element> blocks = element.children().stream()
                    .filter(child -> child.hasClass("ocr-block"))
                    .toList();
            for (Element block : blocks) appendOcrBlock(document, block);
            if (element.nextElementSibling() != null) {
                document.createParagraph().createRun().addBreak(BreakType.PAGE);
            }
            return;
        }
        if (element.hasClass("document-page")) {
            element.childNodes().forEach(child -> appendNode(document, child));
            return;
        }
        // 解析 text-align 和 text-indent
        String style = element.attr("style");
        ParagraphAlignment align = parseAlignment(style);
        Integer indent = parseTextIndent(style, element.attr("data-text-indent"));

        switch (element.tagName()) {
            case "h1" -> appendParagraph(document, element.text(), "Title", indent, align);
            case "h2" -> appendParagraph(document, element.text(), "Heading1", indent, align);
            case "h3" -> appendParagraph(document, element.text(), "Heading2", indent, align);
            case "p", "li" -> appendRichParagraph(document, element, null, indent, align);
            case "div" -> {
                if (element.hasClass("signature-block")) {
                    appendParagraph(document, element.text(), null, indent, align);
                } else {
                    element.childNodes().forEach(child -> appendNode(document, child));
                }
            }
            case "table" -> appendTable(document, element);
            default -> element.childNodes().forEach(child -> appendNode(document, child));
        }
    }

    private void appendOcrBlock(XWPFDocument document, Element block) {
        String type = block.attr("data-block-type").toLowerCase(Locale.ROOT);
        if ("page_number".equals(type)) return;
        String text = block.text();
        if (text.isBlank()) return;

        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(100);
        paragraph.setSpacingBetween(1.35);

        // 对齐：优先读取 data-align，其次 style text-align
        String dataAlign = block.attr("data-align").toLowerCase(Locale.ROOT);
        String style = block.attr("style");
        ParagraphAlignment alignment = dataAlignAlign(dataAlign);
        if (alignment == ParagraphAlignment.LEFT) {
            alignment = parseAlignment(style);
        }
        paragraph.setAlignment(alignment);

        // 首行缩进：优先读取 data-text-indent，其次 style text-indent
        Integer indent = parseTextIndent(style, block.attr("data-text-indent"));
        if (indent == null && ("paragraph".equals(type) || "party_info".equals(type))) {
            // 正文默认缩进 2em，但甲乙方信息不缩进
            indent = "party_info".equals(type) ? 0 : INDENT_2EM_TWIPS;
        }
        if (indent != null && indent > 0) {
            paragraph.setIndentationFirstLine(indent);
        }

        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontFamily("SimSun");
        run.setFontSize(12);
        switch (type) {
            case "title" -> {
                paragraph.setAlignment(ParagraphAlignment.CENTER);
                paragraph.setSpacingAfter(240);
                run.setBold(true);
                run.setFontFamily("SimHei");
                run.setFontSize(18);
            }
            case "heading" -> {
                paragraph.setSpacingBefore(160);
                paragraph.setSpacingAfter(100);
                run.setBold(true);
                run.setFontFamily("SimHei");
                run.setFontSize(14);
            }
            case "signature" -> {
                paragraph.setSpacingBefore(120);
                run.setFontSize(12);
            }
            case "party_info" -> run.setFontSize(12);
            default -> {
            }
        }
    }

    private void configureDocument(XWPFDocument document) {
        var section = document.getDocument().getBody().addNewSectPr();
        var size = section.addNewPgSz();
        size.setW(11906);
        size.setH(16838);
        var margin = section.addNewPgMar();
        margin.setTop(1440);
        margin.setBottom(1440);
        margin.setLeft(1440);
        margin.setRight(1440);
    }

    private void appendParagraph(XWPFDocument document, String text, String style,
                                  Integer firstLineIndent, ParagraphAlignment alignment) {
        XWPFParagraph paragraph = document.createParagraph();
        if (style != null) paragraph.setStyle(style);
        if (alignment != null) paragraph.setAlignment(alignment);
        if (firstLineIndent != null && firstLineIndent > 0) {
            paragraph.setIndentationFirstLine(firstLineIndent);
        }
        XWPFRun run = paragraph.createRun();
        run.setText(text == null ? "" : text);
        if ("Title".equals(style)) {
            run.setBold(true);
            run.setFontSize(18);
        } else if ("Heading1".equals(style)) {
            run.setBold(true);
            run.setFontSize(15);
        } else if ("Heading2".equals(style)) {
            run.setBold(true);
            run.setFontSize(13);
        }
    }

    private void appendRichParagraph(XWPFDocument document, Element element, String style,
                                      Integer firstLineIndent, ParagraphAlignment alignment) {
        XWPFParagraph paragraph = document.createParagraph();
        if (style != null) paragraph.setStyle(style);
        if (alignment != null) paragraph.setAlignment(alignment);
        if (firstLineIndent != null && firstLineIndent > 0) {
            paragraph.setIndentationFirstLine(firstLineIndent);
        }
        appendInlineNodes(paragraph, element.childNodes(), false, null, false);
    }

    private void appendInlineNodes(XWPFParagraph paragraph, List<Node> nodes,
                                   boolean underline, String color, boolean bold) {
        for (Node node : nodes) {
            if (node instanceof TextNode textNode) {
                appendStyledRun(paragraph, textNode.text(), underline, color, bold);
            } else if (node instanceof Element child) {
                if ("br".equals(child.tagName())) {
                    paragraph.createRun().addBreak();
                    continue;
                }
                String childStyle = child.attr("style").toLowerCase(Locale.ROOT);
                boolean childUnderline = underline || childStyle.contains("text-decoration:underline")
                        || childStyle.contains("text-decoration: underline");
                boolean childBold = bold || childStyle.contains("font-weight:bold")
                        || childStyle.contains("font-weight: bold");
                String childColor = parseColor(childStyle);
                appendInlineNodes(paragraph, child.childNodes(), childUnderline,
                        childColor == null ? color : childColor, childBold);
            }
        }
    }

    private void appendStyledRun(XWPFParagraph paragraph, String text,
                                 boolean underline, String color, boolean bold) {
        if (text == null || text.isEmpty()) return;
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontFamily("SimSun");
        run.setFontSize(12);
        if (underline) run.setUnderline(UnderlinePatterns.SINGLE);
        if (bold) run.setBold(true);
        if (color != null) run.setColor(color);
    }

    private String parseColor(String style) {
        if (style == null || style.isBlank()) return null;
        String lower = style.toLowerCase(Locale.ROOT);
        int index = lower.indexOf("color:");
        if (index < 0) return null;
        String value = lower.substring(index + "color:".length()).trim();
        int semi = value.indexOf(';');
        if (semi >= 0) value = value.substring(0, semi).trim();
        if (value.startsWith("#") && value.length() == 7) {
            return value.substring(1).toUpperCase(Locale.ROOT);
        }
        return switch (value) {
            case "red" -> "B91C1C";
            case "orange" -> "B45309";
            case "blue" -> "1D4ED8";
            case "green" -> "15803D";
            default -> null;
        };
    }

    private void appendTable(XWPFDocument document, Element tableElement) {
        var rows = tableElement.select("tr");
        if (rows.isEmpty()) return;
        int columns = Math.max(1, rows.get(0).select("th,td").size());
        XWPFTable table = document.createTable(1, columns);
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            XWPFTableRow row = rowIndex == 0 ? table.getRow(0) : table.createRow();
            var cells = rows.get(rowIndex).select("th,td");
            for (int column = 0; column < columns; column++) {
                String text = column < cells.size() ? cells.get(column).text() : "";
                row.getCell(column).setText(text);
            }
        }
    }

    // ==================== 样式解析工具方法 ====================

    /**
     * 从 HTML inline style 和 data-text-indent 属性中解析首行缩进值（twips）。
     * 支持 "2em" / "32px" 等格式。
     */
    static Integer parseTextIndent(String style, String dataTextIndent) {
        // 优先 data-text-indent
        if (dataTextIndent != null && dataTextIndent.contains("2em")) return INDENT_2EM_TWIPS;
        if (style == null || style.isBlank()) return null;
        String lower = style.toLowerCase(Locale.ROOT);
        int indentIdx = lower.indexOf("text-indent:");
        if (indentIdx < 0) return null;
        String after = lower.substring(indentIdx + "text-indent:".length()).trim();
        // 提取值：到 ; 或字符串末尾
        int semi = after.indexOf(';');
        String value = (semi >= 0 ? after.substring(0, semi) : after).trim();
        if (value.contains("em")) {
            try {
                double em = Double.parseDouble(value.replace("em", "").trim());
                return (int) Math.round(em * 240);
            } catch (NumberFormatException ignored) {
            }
        }
        if (value.contains("px")) {
            try {
                double px = Double.parseDouble(value.replace("px", "").trim());
                return (int) Math.round(px * 15);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /**
     * 从 HTML inline style 中解析 text-align → Word ParagraphAlignment。
     */
    static ParagraphAlignment parseAlignment(String style) {
        if (style == null || style.isBlank()) return ParagraphAlignment.LEFT;
        String lower = style.toLowerCase(Locale.ROOT);
        if (lower.contains("text-align:center") || lower.contains("text-align: center")) {
            return ParagraphAlignment.CENTER;
        }
        if (lower.contains("text-align:right") || lower.contains("text-align: right")) {
            return ParagraphAlignment.RIGHT;
        }
        return ParagraphAlignment.LEFT;
    }

    /**
     * data-align 属性 → Word ParagraphAlignment。
     */
    private static ParagraphAlignment dataAlignAlign(String dataAlign) {
        return switch (dataAlign) {
            case "center" -> ParagraphAlignment.CENTER;
            case "right" -> ParagraphAlignment.RIGHT;
            default -> ParagraphAlignment.LEFT;
        };
    }
}
