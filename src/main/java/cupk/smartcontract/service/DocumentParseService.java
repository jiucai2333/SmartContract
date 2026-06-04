package cupk.smartcontract.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.jsoup.nodes.Entities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class DocumentParseService {
    private static final Logger log = LoggerFactory.getLogger(DocumentParseService.class);
    private static final Pattern ARTICLE = Pattern.compile("^第[一二三四五六七八九十百零〇0-9]+条.*");
    private static final Pattern SECTION = Pattern.compile("^[一二三四五六七八九十百零〇]+、.*");
    private final OcrService ocrService;

    public DocumentParseService(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    public ParseResult parse(Path filePath, String fileType) throws IOException {
        String type = fileType == null ? "" : fileType.toLowerCase(Locale.ROOT);
        if ("docx".equals(type)) return new ParseResult(parseDocx(filePath), 1, "DOCX_POI");
        if (!"pdf".equals(type)) throw new IllegalArgumentException("仅支持 PDF 或 DOCX 格式");

        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            String text = new PDFTextStripper().getText(document);
            int pages = document.getNumberOfPages();
            if (text.replaceAll("\\s+", "").length() >= 50) {
                log.info("[PDFBox] Extracted text PDF directly, characters: {}", text.length());
                return new ParseResult(textToHtml(text), pages, "PDFBOX");
            }
        }

        log.info("[PDFBox] PDF text is insufficient, falling back to PaddleOCR");
        OcrService.OcrProcessResult ocr = ocrService.process(filePath, "pdf");
        return new ParseResult(markdownToHtml(ocr.rawText()), ocr.pageCount(), "PADDLE_OCR");
    }

    private String parseDocx(Path filePath) throws IOException {
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(filePath))) {
            StringBuilder html = new StringBuilder();
            boolean firstText = true;
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText().trim();
                    if (text.isBlank()) continue;
                    html.append(wrapLine(text, firstText));
                    firstText = false;
                } else if (element instanceof XWPFTable table) {
                    html.append("<table><tbody>");
                    for (XWPFTableRow row : table.getRows()) {
                        html.append("<tr>");
                        for (XWPFTableCell cell : row.getTableCells()) {
                            html.append("<td>").append(escape(cell.getText())).append("</td>");
                        }
                        html.append("</tr>");
                    }
                    html.append("</tbody></table>");
                }
            }
            return html.toString();
        }
    }

    private String textToHtml(String text) {
        StringBuilder html = new StringBuilder();
        boolean firstText = true;
        for (String line : text.replace("\r", "").split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) continue;
            html.append(wrapLine(trimmed, firstText));
            firstText = false;
        }
        return html.toString();
    }

    private String markdownToHtml(String markdown) {
        StringBuilder html = new StringBuilder();
        boolean firstText = true;
        for (String line : markdown.replace("\r", "").split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.matches("^[-| :]+$")) continue;
            if (trimmed.startsWith("### ")) html.append(tag("h3", trimmed.substring(4)));
            else if (trimmed.startsWith("## ")) html.append(tag("h2", trimmed.substring(3)));
            else if (trimmed.startsWith("# ")) html.append(tag("h1", trimmed.substring(2)));
            else html.append(wrapLine(trimmed, firstText));
            firstText = false;
        }
        return html.toString();
    }

    private String wrapLine(String text, boolean firstText) {
        if (ARTICLE.matcher(text).matches()) return tag("h2", text);
        if (SECTION.matcher(text).matches()) return tag("h3", text);
        if (firstText && text.length() <= 60) return tag("h1", text);
        return tag("p", text);
    }

    private String tag(String name, String text) {
        return "<" + name + ">" + escape(text) + "</" + name + ">";
    }

    private String escape(String text) {
        return Entities.escape(text == null ? "" : text);
    }

    public record ParseResult(String html, int pageCount, String parser) {}
}
