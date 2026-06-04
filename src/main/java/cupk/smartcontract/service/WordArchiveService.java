package cupk.smartcontract.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class WordArchiveService {

    public byte[] toDocx(String html) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Element body = Jsoup.parseBodyFragment(html == null ? "" : html).body();
            for (Node node : body.childNodes()) {
                appendNode(document, node);
            }
            document.write(output);
            return output.toByteArray();
        }
    }

    private void appendNode(XWPFDocument document, Node node) {
        if (node instanceof TextNode textNode) {
            if (!textNode.text().isBlank()) appendParagraph(document, textNode.text(), null);
            return;
        }
        if (!(node instanceof Element element)) return;
        switch (element.tagName()) {
            case "h1" -> appendParagraph(document, element.text(), "Title");
            case "h2" -> appendParagraph(document, element.text(), "Heading1");
            case "h3" -> appendParagraph(document, element.text(), "Heading2");
            case "p", "div", "li" -> appendParagraph(document, element.text(), null);
            case "table" -> appendTable(document, element);
            default -> element.childNodes().forEach(child -> appendNode(document, child));
        }
    }

    private void appendParagraph(XWPFDocument document, String text, String style) {
        XWPFParagraph paragraph = document.createParagraph();
        if (style != null) paragraph.setStyle(style);
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
}
