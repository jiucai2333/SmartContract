package cupk.smartcontract.service;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabTlc;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WordArchiveService {

    /** Body text defaults to 14pt; character indents are derived from the active font size. */
    private static final int DEFAULT_BODY_FONT_SIZE_PT = 14;
    private static final int SIGNATURE_TAB_STOP_TWIPS = 4150;
    private static final int SIGNATURE_BLOCK_TOP_TWIPS = 720;
    private static final int SIGNATURE_ROW_AFTER_TWIPS = 300;

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
            if (!textNode.text().isBlank()) appendStyledParagraph(document, textNode.text(), null);
            return;
        }
        if (!(node instanceof Element element)) return;
        if (element.hasClass("ocr-page")) {
            element.children().stream()
                    .filter(child -> child.hasClass("ocr-block"))
                    .forEach(block -> appendStyledParagraph(document, block.text(), block));
            return;
        }
        if (element.hasClass("document-page")) {
            appendDocumentPage(document, element);
            return;
        }
        if (isSignatureBlockWithRows(element)) {
            appendSignatureRows(document, element);
            return;
        }
        if ("table".equals(element.tagName())) {
            appendTable(document, element);
            return;
        }
        if (element.childNodeSize() > 0 && !isLeafSemanticElement(element)) {
            element.childNodes().forEach(child -> appendNode(document, child));
            return;
        }
        appendStyledParagraph(document, element);
    }

    private void appendDocumentPage(XWPFDocument document, Element page) {
        List<Element> children = page.children();
        for (int i = 0; i < children.size(); i++) {
            Element current = children.get(i);
            if (isSignatureLeft(current) && i + 1 < children.size()
                    && isSignatureRight(children.get(i + 1))) {
                appendSignaturePairParagraph(document, current.text(), children.get(i + 1).text(), true);
                i++;
                continue;
            }
            appendNode(document, current);
        }
    }

    private boolean isLeafSemanticElement(Element element) {
        return List.of("h1", "h2", "h3", "h4", "h5", "h6", "p", "li", "div")
                .contains(element.tagName());
    }

    private void appendStyledParagraph(XWPFDocument document, Element element) {
        String docStyle = resolveDocStyle(element);
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBetween(resolveLineHeight(element));
        paragraph.setAlignment(resolveAlignment(element));
        applyParagraphDocStyle(paragraph, docStyle);
        applyIndent(paragraph, element, resolveFontSizePt(docStyle));
        appendInlineNodes(paragraph, element.childNodes(),
                InlineStyle.fromElement(element), docStyle);
        if (paragraph.getRuns().isEmpty()) {
            appendTextRun(paragraph, "", InlineStyle.fromElement(element), docStyle);
        }
    }

    private void appendStyledParagraph(XWPFDocument document, String text, Element element) {
        String docStyle = element == null ? "BODY" : resolveDocStyle(element);
        ParagraphAlignment align = element == null
                ? ParagraphAlignment.LEFT : resolveAlignment(element);
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBetween(1.5);
        paragraph.setAlignment(align);

        applyParagraphDocStyle(paragraph, docStyle);
        appendTextRun(paragraph, text == null ? "" : text,
                element == null ? InlineStyle.EMPTY : InlineStyle.fromElement(element), docStyle);
        if (element != null) {
            applyIndent(paragraph, element, resolveFontSizePt(docStyle));
        }
    }

    private boolean isSignatureBlockWithRows(Element element) {
        if (!(element.hasClass("signature-block")
                || "SIGNATURE_BLOCK".equals(element.attr("data-doc-style")))) {
            return false;
        }
        return element.children().stream().anyMatch(child -> child.hasClass("signature-row"));
    }

    private void appendSignatureRows(XWPFDocument document, Element signatureBlock) {
        List<Element> rows = signatureBlock.children().stream()
                .filter(child -> child.hasClass("signature-row"))
                .toList();
        for (int i = 0; i < rows.size(); i++) {
            appendSignatureRowParagraph(document, rows.get(i), i == 0);
        }
    }

    private void appendSignatureRowParagraph(XWPFDocument document, Element row, boolean firstRow) {
        List<Element> spans = row.children().stream()
                .filter(child -> "span".equals(child.tagName()))
                .toList();
        String left = spans.isEmpty() ? row.text() : spans.get(0).text();
        String right = spans.size() >= 2 ? spans.get(1).text() : "";
        appendSignaturePairParagraph(document, left, right, firstRow);
    }

    private void appendSignaturePairParagraph(XWPFDocument document, String left, String right,
                                              boolean firstRow) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        paragraph.setSpacingBetween(2.0);
        paragraph.setSpacingAfter(SIGNATURE_ROW_AFTER_TWIPS);
        if (firstRow) paragraph.setSpacingBefore(SIGNATURE_BLOCK_TOP_TWIPS);
        addTabStop(paragraph, SIGNATURE_TAB_STOP_TWIPS);

        appendSignatureRun(paragraph, left);
        if (StringUtils.hasText(right)) {
            paragraph.createRun().addTab();
            appendSignatureRun(paragraph, right);
        }
    }

    private void appendSignatureRun(XWPFParagraph paragraph, String text) {
        XWPFRun run = paragraph.createRun();
        run.setText(text == null ? "" : text);
        run.setFontFamily("FangSong");
        run.setFontSize(DEFAULT_BODY_FONT_SIZE_PT);
    }

    private void addTabStop(XWPFParagraph paragraph, int positionTwips) {
        var pPr = paragraph.getCTP().isSetPPr()
                ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        var tabs = pPr.isSetTabs() ? pPr.getTabs() : pPr.addNewTabs();
        var tab = tabs.addNewTab();
        tab.setVal(STTabJc.LEFT);
        tab.setLeader(STTabTlc.NONE);
        tab.setPos(BigInteger.valueOf(positionTwips));
    }

    private void applyParagraphDocStyle(XWPFParagraph paragraph, String docStyle) {
        switch (docStyle) {
            case "TITLE" -> {
                paragraph.setAlignment(ParagraphAlignment.CENTER);
                paragraph.setSpacingAfter(240);
            }
            case "CLAUSE_TITLE", "SECTION_HEADING" -> {
                paragraph.setSpacingBefore(160);
                paragraph.setSpacingAfter(100);
            }
            case "SIGNATURE", "SIGNATURE_LEFT", "SIGNATURE_RIGHT", "SIGNATURE_BLOCK" -> {
                paragraph.setSpacingBefore(120);
                if ("SIGNATURE_LEFT".equals(docStyle)) {
                    paragraph.setAlignment(ParagraphAlignment.LEFT);
                } else if ("SIGNATURE_RIGHT".equals(docStyle)) {
                    paragraph.setAlignment(ParagraphAlignment.RIGHT);
                }
            }
            default -> { }
        }
    }

    private void appendInlineNodes(XWPFParagraph paragraph, List<Node> nodes,
                                   InlineStyle inherited, String docStyle) {
        for (Node node : nodes) {
            if (node instanceof TextNode textNode) {
                appendTextRun(paragraph, textNode.getWholeText(), inherited, docStyle);
            } else if (node instanceof Element child) {
                if ("br".equals(child.tagName())) {
                    paragraph.createRun().addBreak();
                } else if (isEmptyPlaceholderUnderline(child)) {
                    InlineStyle style = inherited.merge(child);
                    appendTextRun(paragraph, placeholderSpaces(child), style, docStyle);
                } else {
                    appendInlineNodes(paragraph, child.childNodes(),
                            inherited.merge(child), docStyle);
                }
            }
        }
    }

    private boolean isEmptyPlaceholderUnderline(Element element) {
        return element.hasClass("contract-fill-underline")
                && "true".equals(element.attr("data-placeholder-empty"));
    }

    private String placeholderSpaces(Element element) {
        String token = element.attr("data-placeholder-text");
        int length = StringUtils.hasText(token) ? token.length() : element.text().length();
        return " ".repeat(Math.max(6, length));
    }

    private void appendTextRun(XWPFParagraph paragraph, String text, InlineStyle style, String docStyle) {
        if (text == null || text.isEmpty()) return;
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        boolean heading = "TITLE".equals(docStyle)
                || "CLAUSE_TITLE".equals(docStyle)
                || "SECTION_HEADING".equals(docStyle);
        run.setBold(style.bold != null ? style.bold : heading);
        run.setItalic(Boolean.TRUE.equals(style.italic));
        run.setUnderline(Boolean.TRUE.equals(style.underline)
                ? org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE
                : org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE);
        run.setStrikeThrough(Boolean.TRUE.equals(style.strike));
        if (Boolean.TRUE.equals(style.superscript)) {
            run.setSubscript(org.apache.poi.xwpf.usermodel.VerticalAlign.SUPERSCRIPT);
        } else if (Boolean.TRUE.equals(style.subscript)) {
            run.setSubscript(org.apache.poi.xwpf.usermodel.VerticalAlign.SUBSCRIPT);
        }
        run.setFontFamily(StringUtils.hasText(style.fontFamily) ? style.fontFamily : "FangSong");
        run.setFontSize(style.fontSizePt != null ? style.fontSizePt
                : ("TITLE".equals(docStyle) ? 18 : DEFAULT_BODY_FONT_SIZE_PT));
        if (StringUtils.hasText(style.color)) run.setColor(style.color);
        if (StringUtils.hasText(style.backgroundColor)) {
            var rPr = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
            var shd = rPr.sizeOfShdArray() > 0 ? rPr.getShdArray(0) : rPr.addNewShd();
            shd.setFill(style.backgroundColor);
        }
    }

    private double resolveLineHeight(Element element) {
        String value = styleValue(element.attr("style"), "line-height");
        if (!StringUtils.hasText(value)) return 1.5;
        try {
            if (value.endsWith("%")) return Double.parseDouble(value.replace("%", "")) / 100.0;
            if (value.matches("\\d+(?:\\.\\d+)?")) return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
        }
        return 1.5;
    }

    private static String styleValue(String style, String property) {
        if (!StringUtils.hasText(style)) return null;
        Matcher matcher = Pattern.compile(
                "(?i)(?:^|;)\\s*" + Pattern.quote(property) + "\\s*:\\s*([^;]+)")
                .matcher(style);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static String normalizeColor(String value) {
        if (!StringUtils.hasText(value)) return null;
        String color = value.trim();
        if (color.matches("(?i)^#[0-9a-f]{3}$")) {
            return ("" + color.charAt(1) + color.charAt(1)
                    + color.charAt(2) + color.charAt(2)
                    + color.charAt(3) + color.charAt(3)).toUpperCase(Locale.ROOT);
        }
        if (color.matches("(?i)^#[0-9a-f]{6,8}$")) {
            return color.substring(1, 7).toUpperCase(Locale.ROOT);
        }
        Matcher rgb = Pattern.compile(
                "(?i)^rgba?\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})")
                .matcher(color);
        if (rgb.find()) {
            return String.format("%02X%02X%02X",
                    Math.min(255, Integer.parseInt(rgb.group(1))),
                    Math.min(255, Integer.parseInt(rgb.group(2))),
                    Math.min(255, Integer.parseInt(rgb.group(3))));
        }
        return null;
    }

    private static Integer fontSizePt(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (normalized.endsWith("px")) {
                return Math.max(1, (int) Math.round(
                        Double.parseDouble(normalized.replace("px", "")) * 0.75));
            }
            if (normalized.endsWith("pt")) {
                return Math.max(1, (int) Math.round(
                        Double.parseDouble(normalized.replace("pt", ""))));
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private record InlineStyle(Boolean bold, Boolean italic, Boolean underline,
                               Boolean strike, Boolean superscript, Boolean subscript,
                               String color, String backgroundColor,
                               String fontFamily, Integer fontSizePt) {
        private static final InlineStyle EMPTY = new InlineStyle(
                null, null, null, null, null, null, null, null, null, null);

        private static InlineStyle fromElement(Element element) {
            return EMPTY.merge(element);
        }

        private InlineStyle merge(Element element) {
            String tag = element.tagName().toLowerCase(Locale.ROOT);
            String style = element.attr("style");
            String weight = styleValue(style, "font-weight");
            String family = styleValue(style, "font-family");
            String decoration = styleValue(style, "text-decoration");
            String borderBottom = styleValue(style, "border-bottom");
            if (StringUtils.hasText(family)) {
                family = family.split(",")[0].replace("\"", "").replace("'", "").trim();
            }
            boolean boldTag = "strong".equals(tag) || "b".equals(tag)
                    || "bold".equalsIgnoreCase(weight)
                    || weight != null && weight.matches("[6-9]00");
            boolean underlineTag = "u".equals(tag)
                    || element.hasClass("contract-fill-underline")
                    || decoration != null && decoration.toLowerCase(Locale.ROOT).contains("underline")
                    || borderBottom != null && !borderBottom.toLowerCase(Locale.ROOT).contains("none");
            return new InlineStyle(
                    boldTag ? Boolean.TRUE : bold,
                    ("em".equals(tag) || "i".equals(tag)) ? Boolean.TRUE : italic,
                    underlineTag ? Boolean.TRUE : underline,
                    ("s".equals(tag) || "strike".equals(tag) || "del".equals(tag))
                            ? Boolean.TRUE : strike,
                    "sup".equals(tag) ? Boolean.TRUE : superscript,
                    "sub".equals(tag) ? Boolean.TRUE : subscript,
                    firstNonBlank(normalizeColor(styleValue(style, "color")), color),
                    firstNonBlank(normalizeColor(styleValue(style, "background-color")), backgroundColor),
                    firstNonBlank(family, fontFamily),
                    firstNonNull(WordArchiveService.fontSizePt(
                            styleValue(style, "font-size")), fontSizePt));
        }

        private static String firstNonBlank(String preferred, String fallback) {
            return StringUtils.hasText(preferred) ? preferred : fallback;
        }

        private static Integer firstNonNull(Integer preferred, Integer fallback) {
            return preferred != null ? preferred : fallback;
        }
    }

    private String resolveDocStyle(Element element) {
        String docStyle = element.attr("data-doc-style");
        if (StringUtils.hasText(docStyle)) return docStyle;
        String blockType = element.attr("data-block-type").toLowerCase(Locale.ROOT);
        if ("title".equals(blockType)) return "TITLE";
        if ("heading".equals(blockType) || "section_heading".equals(blockType)) {
            return "SECTION_HEADING";
        }
        if ("field_line".equals(blockType) || "party_info".equals(blockType)) return "FIELD_LINE";
        if ("date_line".equals(blockType)) return "DATE_LINE";
        if ("signature".equals(blockType) || "stamp".equals(blockType)) return "SIGNATURE_BLOCK";
        if (element.hasClass("clause-title")) return "SECTION_HEADING";
        if (element.hasClass("signature-block")) {
            String column = element.attr("data-column").toLowerCase(Locale.ROOT);
            if ("left".equals(column)) return "SIGNATURE_LEFT";
            if ("right".equals(column)) return "SIGNATURE_RIGHT";
            return "SIGNATURE_BLOCK";
        }
        return switch (element.tagName()) {
            case "h1" -> "TITLE";
            case "h2", "h3" -> "SECTION_HEADING";
            default -> "CLAUSE_BODY";
        };
    }

    private ParagraphAlignment resolveAlignment(Element element) {
        ParagraphAlignment styleAlign = parseAlignment(element.attr("style"));
        if (styleAlign != null) return styleAlign;
        String dataAlign = element.attr("data-align").toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(dataAlign)) {
            return dataAlignAlign(dataAlign);
        }
        return ParagraphAlignment.LEFT;
    }

    private void applyIndent(XWPFParagraph paragraph, Element element, int fontSizePt) {
        Integer indent = parseTextIndent(
                element.attr("style"), element.attr("data-text-indent"), fontSizePt);
        if (indent != null && indent >= 0) {
            paragraph.setIndentationFirstLine(indent);
            return;
        }
        Integer indentChars = parseIndentChars(element.attr("data-indent-chars"));
        if (indentChars != null) {
            setCharacterIndent(paragraph, indentChars, fontSizePt);
        }
    }

    private void setCharacterIndent(XWPFParagraph paragraph, int indentChars, int fontSizePt) {
        int firstLineTwips = Math.max(0, fontSizePt * 20 * indentChars);
        paragraph.setIndentationFirstLine(firstLineTwips);
        var pPr = paragraph.getCTP().isSetPPr()
                ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        var ind = pPr.isSetInd() ? pPr.getInd() : pPr.addNewInd();
        ind.setFirstLine(BigInteger.valueOf(firstLineTwips));
        ind.setFirstLineChars(BigInteger.valueOf(Math.max(0, indentChars * 100L)));
    }

    private int resolveFontSizePt(String docStyle) {
        return "TITLE".equals(docStyle) ? 18 : DEFAULT_BODY_FONT_SIZE_PT;
    }

    private boolean isSignatureLeft(Element element) {
        return "SIGNATURE_LEFT".equals(resolveDocStyle(element))
                || "left".equals(element.attr("data-column").toLowerCase(Locale.ROOT));
    }

    private boolean isSignatureRight(Element element) {
        return "SIGNATURE_RIGHT".equals(resolveDocStyle(element))
                || "right".equals(element.attr("data-column").toLowerCase(Locale.ROOT));
    }

    private void configureDocument(XWPFDocument document) {
        var section = document.getDocument().getBody().addNewSectPr();
        var size = section.addNewPgSz();
        size.setW(11906);
        size.setH(16838);
        var margin = section.addNewPgMar();
        margin.setTop(1440);
        margin.setBottom(1440);
        margin.setLeft(1803);
        margin.setRight(1803);
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
                XWPFTableCell cell = row.getCell(column);
                cell.removeParagraph(0);
                XWPFParagraph paragraph = cell.addParagraph();
                paragraph.setSpacingBetween(1.5);
                ParagraphAlignment alignment = parseAlignment(
                        column < cells.size() ? cells.get(column).attr("style") : null);
                paragraph.setAlignment(alignment != null ? alignment : ParagraphAlignment.LEFT);
                XWPFRun run = paragraph.createRun();
                run.setText(text);
                run.setFontFamily("FangSong");
                run.setFontSize(14);
            }
        }
        if (tableElement.hasClass("signature-table")) hideTableBorders(table);
    }

    private void hideTableBorders(XWPFTable table) {
        var borders = table.getCTTbl().getTblPr().isSetTblBorders()
                ? table.getCTTbl().getTblPr().getTblBorders()
                : table.getCTTbl().getTblPr().addNewTblBorders();
        var nil = org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NIL;
        (borders.isSetTop() ? borders.getTop() : borders.addNewTop()).setVal(nil);
        (borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom()).setVal(nil);
        (borders.isSetLeft() ? borders.getLeft() : borders.addNewLeft()).setVal(nil);
        (borders.isSetRight() ? borders.getRight() : borders.addNewRight()).setVal(nil);
        (borders.isSetInsideH() ? borders.getInsideH() : borders.addNewInsideH()).setVal(nil);
        (borders.isSetInsideV() ? borders.getInsideV() : borders.addNewInsideV()).setVal(nil);
    }

    static Integer parseIndentChars(String indentChars) {
        if (indentChars == null || indentChars.isBlank()) return null;
        try {
            return Math.max(0, Integer.parseInt(indentChars.trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 从 HTML inline style 和 data-text-indent 属性中解析首行缩进值（twips）。
     * 支持 "2em" / "32px" 等格式。
     */
    static Integer parseTextIndent(String style, String dataTextIndent, int fontSizePt) {
        if (dataTextIndent != null && dataTextIndent.contains("2em")) {
            return fontSizePt * 20 * 2;
        }
        if (style == null || style.isBlank()) return null;
        String lower = style.toLowerCase(Locale.ROOT);
        int indentIdx = lower.indexOf("text-indent:");
        if (indentIdx < 0) return null;
        String after = lower.substring(indentIdx + "text-indent:".length()).trim();
        int semi = after.indexOf(';');
        String value = (semi >= 0 ? after.substring(0, semi) : after).trim();
        if ("0".equals(value) || value.startsWith("0")) return 0;
        if (value.contains("em")) {
            try {
                double em = Double.parseDouble(value.replace("em", "").trim());
                return (int) Math.round(em * fontSizePt * 20);
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
        if (style == null || style.isBlank()) return null;
        String lower = style.toLowerCase(Locale.ROOT);
        if (lower.contains("text-align:center") || lower.contains("text-align: center")) {
            return ParagraphAlignment.CENTER;
        }
        if (lower.contains("text-align:right") || lower.contains("text-align: right")) {
            return ParagraphAlignment.RIGHT;
        }
        if (lower.contains("text-align:left") || lower.contains("text-align: left")) {
            return ParagraphAlignment.LEFT;
        }
        return null;
    }

    private static ParagraphAlignment dataAlignAlign(String dataAlign) {
        return switch (dataAlign) {
            case "center" -> ParagraphAlignment.CENTER;
            case "right" -> ParagraphAlignment.RIGHT;
            default -> ParagraphAlignment.LEFT;
        };
    }
}
