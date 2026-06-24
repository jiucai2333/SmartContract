package cupk.smartcontract.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.CharacterRun;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.xwpf.usermodel.*;
import org.jsoup.nodes.Entities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentParseService {
    private static final Logger log = LoggerFactory.getLogger(DocumentParseService.class);
    private final OcrService ocrService;
    private final OcrEditorHtmlService ocrEditorHtmlService;

    public DocumentParseService(OcrService ocrService, OcrEditorHtmlService ocrEditorHtmlService) {
        this.ocrService = ocrService;
        this.ocrEditorHtmlService = ocrEditorHtmlService;
    }

    public ParseResult parse(Path filePath, String fileType) throws IOException {
        return parse(filePath, fileType, false);
    }

    public ParseResult parse(Path filePath, String fileType, boolean preserveFormat) throws IOException {
        String type = fileType == null ? "" : fileType.toLowerCase(Locale.ROOT);
        if ("docx".equals(type)) return new ParseResult(parseDocx(filePath, preserveFormat), 1, "DOCX_POI");
        if ("doc".equals(type)) return new ParseResult(parseDoc(filePath, preserveFormat), 1, "DOC_HWPF");
        if (!"pdf".equals(type)) throw new IllegalArgumentException("仅支持 PDF、DOC 或 DOCX 格式");

        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            String text = new PDFTextStripper().getText(document);
            int pages = document.getNumberOfPages();
            if (text.replaceAll("\\s+", "").length() >= 50) {
                return new ParseResult(ocrEditorHtmlService.buildPlainTextHtml(text), pages, "PDFBOX");
            }
        }
        OcrService.OcrProcessResult ocr = ocrService.process(filePath, "pdf");
        return new ParseResult(
                preserveFormat && ocr.formattedHtml() != null
                        ? ocr.formattedHtml()
                        : textToHtml(ocr.rawText(), preserveFormat),
                ocr.pageCount(),
                preserveFormat ? "OCR_FORMATTED" : "OCR");
    }

    private String parseDocx(Path filePath, boolean preserve) throws IOException {
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(filePath))) {
            StringBuilder html = new StringBuilder();
            MergeMap mergeMap = collectMerges(document);
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph p) html.append(docxParagraph(p, preserve));
                else if (element instanceof XWPFTable table) html.append(docxTable(table, preserve, mergeMap.merges, mergeMap.skipped));
            }
            return html.toString();
        } catch (Exception ex) {
            throw new IOException("DOCX 文件损坏或无法解析: " + ex.getMessage(), ex);
        }
    }

    private String docxParagraph(XWPFParagraph paragraph, boolean preserve) {
        String content;
        if (!preserve) {
            content = escape(paragraph.getText().trim());
        } else {
            StringBuilder out = new StringBuilder();
            for (XWPFRun run : paragraph.getRuns()) {
                String value = run.text();
                if (value == null) continue;
                String rendered = preserveSpaces(escape(value)).replace("\n", "<br>");
                if (run.isBold()) rendered = "<strong>" + rendered + "</strong>";
                if (run.isItalic()) rendered = "<em>" + rendered + "</em>";
                if (run.getUnderline() != UnderlinePatterns.NONE && !value.matches("_+")) rendered = "<u>" + rendered + "</u>";
                out.append(rendered);
                int breaks = run.getCTR().sizeOfBrArray();
                if (breaks > 0) out.append("<br>".repeat(breaks));
            }
            content = out.toString();
        }
        if (content.isBlank() && !preserve) return "";
        List<String> styles = new ArrayList<>();
        if (preserve) {
            ParagraphAlignment alignment = paragraph.getAlignment();
            if (alignment == ParagraphAlignment.CENTER) styles.add("text-align:center");
            else if (alignment == ParagraphAlignment.RIGHT) styles.add("text-align:right");
            int indent = paragraph.getFirstLineIndent();
            if (indent != 0) styles.add("text-indent:" + format(indent / 240.0) + "em");
            int after = paragraph.getSpacingAfter();
            if (after >= 0) styles.add("margin-bottom:" + format(after / 20.0) + "pt");
            styles.add("margin-top:0");
        }
        return "<p" + style(styles) + ">" + content + "</p>";
    }

    private String docxTable(XWPFTable table, boolean preserve, Map<XWPFTableCell, MergeInfo> merges,
                             Set<XWPFTableCell> skipped) {
        StringBuilder html = new StringBuilder("<table><tbody>");
        for (XWPFTableRow row : table.getRows()) {
            html.append("<tr>");
            for (XWPFTableCell cell : row.getTableCells()) {
                if (skipped.contains(cell)) continue;
                MergeInfo merge = merges.getOrDefault(cell, new MergeInfo(1, 1));
                html.append("<td");
                if (merge.colspan > 1) html.append(" colspan=\"").append(merge.colspan).append("\"");
                if (merge.rowspan > 1) html.append(" rowspan=\"").append(merge.rowspan).append("\"");
                html.append(">");
                for (XWPFParagraph p : cell.getParagraphs()) html.append(docxParagraph(p, preserve));
                html.append("</td>");
            }
            html.append("</tr>");
        }
        return html.append("</tbody></table>").toString();
    }

    private MergeMap collectMerges(XWPFDocument document) {
        Map<XWPFTableCell, MergeInfo> result = new IdentityHashMap<>();
        Set<XWPFTableCell> skipped = Collections.newSetFromMap(new IdentityHashMap<>());
        for (XWPFTable table : document.getTables()) {
            List<XWPFTableRow> rows = table.getRows();
            for (int r = 0; r < rows.size(); r++) {
                List<XWPFTableCell> cells = rows.get(r).getTableCells();
                for (int c = 0; c < cells.size(); c++) {
                    XWPFTableCell cell = cells.get(c);
                    int gridSpan = cell.getCTTc().getTcPr() != null && cell.getCTTc().getTcPr().isSetGridSpan()
                            ? cell.getCTTc().getTcPr().getGridSpan().getVal().intValue() : 1;
                    int rowSpan = 1;
                    if (cell.getCTTc().getTcPr() != null && cell.getCTTc().getTcPr().isSetVMerge()
                            && cell.getCTTc().getTcPr().getVMerge().getVal() != null) {
                        for (int rr = r + 1; rr < rows.size(); rr++) {
                            if (c >= rows.get(rr).getTableCells().size()) break;
                            XWPFTableCell next = rows.get(rr).getCell(c);
                            if (next.getCTTc().getTcPr() == null || !next.getCTTc().getTcPr().isSetVMerge()
                                    || next.getCTTc().getTcPr().getVMerge().getVal() != null) break;
                            rowSpan++;
                            skipped.add(next);
                        }
                    }
                    result.put(cell, new MergeInfo(gridSpan, rowSpan));
                }
            }
        }
        return new MergeMap(result, skipped);
    }

    private String parseDoc(Path filePath, boolean preserve) throws IOException {
        try (HWPFDocument document = new HWPFDocument(Files.newInputStream(filePath))) {
            Range range = document.getRange();
            StringBuilder html = new StringBuilder();
            for (int i = 0; i < range.numParagraphs(); i++) {
                Paragraph p = range.getParagraph(i);
                List<String> styles = new ArrayList<>();
                if (preserve) {
                    if (p.getJustification() == 1) styles.add("text-align:center");
                    else if (p.getJustification() == 2) styles.add("text-align:right");
                    if (p.getFirstLineIndent() != 0) styles.add("text-indent:" + format(p.getFirstLineIndent() / 240.0) + "em");
                    if (p.getSpacingAfter() > 0) styles.add("margin-bottom:" + format(p.getSpacingAfter() / 20.0) + "pt");
                    styles.add("margin-top:0");
                }
                StringBuilder content = new StringBuilder();
                for (int j = 0; j < p.numCharacterRuns(); j++) {
                    CharacterRun run = p.getCharacterRun(j);
                    String raw = run.text().replace("\r", "").replace("\u0007", "");
                    String value = preserve ? preserveSpaces(escape(raw)).replace("\u000B", "<br>") : escape(raw.trim());
                    if (run.isBold()) value = "<strong>" + value + "</strong>";
                    if (run.isItalic()) value = "<em>" + value + "</em>";
                    if (run.getUnderlineCode() != 0 && !raw.matches("_+")) value = "<u>" + value + "</u>";
                    content.append(value);
                }
                if (!content.isEmpty() || preserve) html.append("<p").append(style(styles)).append(">")
                        .append(content).append("</p>");
            }
            return html.toString();
        } catch (Exception ex) {
            throw new IOException("DOC 文件损坏或无法解析: " + ex.getMessage(), ex);
        }
    }

    private String textToHtml(String text, boolean preserve) {
        StringBuilder html = new StringBuilder();
        for (String line : text.replace("\r", "").split("\n", -1)) {
            if (line.isBlank()) {
                if (preserve) html.append("<p style=\"margin:0\"><br></p>");
            } else {
                html.append("<p style=\"margin:0\">")
                        .append(preserve ? preserveSpaces(escape(line)) : escape(line.trim()))
                        .append("</p>");
            }
        }
        return html.toString();
    }

    private String preserveSpaces(String text) {
        Matcher matcher = Pattern.compile(" {2,}").matcher(text);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) matcher.appendReplacement(output, Matcher.quoteReplacement("&nbsp;".repeat(matcher.group().length())));
        matcher.appendTail(output);
        return output.toString();
    }

    private String style(List<String> styles) {
        return styles.isEmpty() ? "" : " style=\"" + String.join(";", styles) + "\"";
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("\\.?0+$", "");
    }

    private String escape(String text) {
        return Entities.escape(text == null ? "" : text);
    }

    private record MergeInfo(int colspan, int rowspan) {}
    private record MergeMap(Map<XWPFTableCell, MergeInfo> merges, Set<XWPFTableCell> skipped) {}
    public record ParseResult(String html, int pageCount, String parser) {}
}
