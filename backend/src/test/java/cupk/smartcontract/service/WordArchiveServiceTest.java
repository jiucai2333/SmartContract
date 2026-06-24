package cupk.smartcontract.service;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class WordArchiveServiceTest {
    private final WordArchiveService service = new WordArchiveService();

    @Test
    void exportsHtmlIndentWithoutGuessingAndUsesUnifiedTypography() throws Exception {
        String html = """
                <div class="document-page">
                  <h1 style="text-align:center">采购合同</h1>
                  <p class="clause-title">一、合同标的</p>
                  <p style="text-indent:0">不缩进正文</p>
                  <p style="text-indent:2em">缩进正文</p>
                  <div class="signature-block">甲方签名</div>
                </div>
                """;

        try (XWPFDocument document =
                     new XWPFDocument(new ByteArrayInputStream(service.toDocx(html)))) {
            assertThat(document.getParagraphs()).hasSize(5);
            assertThat(document.getParagraphs().get(0).getAlignment())
                    .isEqualTo(ParagraphAlignment.CENTER);
            assertThat(document.getParagraphs().get(2).getIndentationFirstLine()).isLessThanOrEqualTo(0);
            assertThat(document.getParagraphs().get(3).getIndentationFirstLine()).isEqualTo(560);
            assertThat(document.getParagraphs().get(2).getSpacingBetween()).isEqualTo(1.5);
            assertThat(document.getParagraphs().get(2).getRuns().get(0).getFontFamily())
                    .isEqualTo("FangSong");
            assertThat(document.getParagraphs().get(2).getRuns().get(0).getFontSize())
                    .isEqualTo(14);
            assertThat(document.getParagraphs().get(4).getRuns().get(0).getFontFamily())
                    .isEqualTo("FangSong");
            assertThat(String.valueOf(
                    document.getDocument().getBody().getSectPr().getPgMar().getLeft()))
                    .isEqualTo("1803");
            assertThat(String.valueOf(
                    document.getDocument().getBody().getSectPr().getPgMar().getRight()))
                    .isEqualTo("1803");
            assertThat(document.getParagraphs().get(1).getRuns().get(0).isBold()).isTrue();
        }
    }

    @Test
    void exportsDataAttributesForAlignIndentAndSignatureColumns() throws Exception {
        String html = """
                <div class="document-page">
                  <p data-doc-style="CLAUSE_BODY" data-align="center" data-indent-chars="2">居中缩进正文</p>
                  <div data-doc-style="SIGNATURE_LEFT" data-column="left">甲方签名</div>
                  <div data-doc-style="SIGNATURE_RIGHT" data-column="right">乙方签名</div>
                </div>
                """;

        try (XWPFDocument document =
                     new XWPFDocument(new ByteArrayInputStream(service.toDocx(html)))) {
            assertThat(document.getParagraphs().get(0).getAlignment())
                    .isEqualTo(ParagraphAlignment.CENTER);
            assertThat(document.getParagraphs().get(0).getIndentationFirstLine()).isEqualTo(560);
            assertThat(document.getParagraphs().get(0).getCTP().getPPr().getInd()
                    .getFirstLineChars()).isEqualTo(java.math.BigInteger.valueOf(200));
            assertThat(document.getTables()).isEmpty();
            assertThat(document.getParagraphs()).hasSize(2);
            assertThat(document.getParagraphs().get(1).getText())
                    .contains("甲方签名", "乙方签名");
            assertThat(document.getParagraphs().get(1).getCTP().getPPr().getTabs()
                    .sizeOfTabArray()).isGreaterThan(0);
        }
    }

    @Test
    void inlineParagraphStylesOverrideImportedLayoutAttributes() throws Exception {
        String html = """
                <div class="document-page">
                  <p data-align="center" data-indent-chars="2" style="text-align:right;text-indent:0">用户调整后的正文</p>
                </div>
                """;

        try (XWPFDocument document =
                     new XWPFDocument(new ByteArrayInputStream(service.toDocx(html)))) {
            assertThat(document.getParagraphs().get(0).getAlignment())
                    .isEqualTo(ParagraphAlignment.RIGHT);
            assertThat(document.getParagraphs().get(0).getIndentationFirstLine())
                    .isLessThanOrEqualTo(0);
        }
    }

    @Test
    void exportsSignatureRowsAsTabbedParagraphsWithoutTables() throws Exception {
        String html = """
                <div class="signature-block" data-doc-style="SIGNATURE_BLOCK" data-indent-chars="0" style="margin-top:48px;text-indent:0;">
                  <div class="signature-row" style="display:grid;grid-template-columns:1fr 1fr;margin-bottom:20px;line-height:2;text-indent:0;">
                    <span style="white-space:pre-wrap;text-indent:0;">甲方</span>
                    <span style="white-space:pre-wrap;text-indent:0;">乙方</span>
                  </div>
                  <div class="signature-row" style="display:grid;grid-template-columns:1fr 1fr;margin-bottom:20px;line-height:2;text-indent:0;">
                    <span style="white-space:pre-wrap;text-indent:0;">签名：___ （盖章）</span>
                    <span style="white-space:pre-wrap;text-indent:0;">签名：___ （盖章）</span>
                  </div>
                  <div class="signature-row" style="display:grid;grid-template-columns:1fr 1fr;margin-bottom:20px;line-height:2;text-indent:0;">
                    <span style="white-space:pre-wrap;text-indent:0;">签署日期：___年___月___日</span>
                  </div>
                </div>
                """;

        try (XWPFDocument document =
                     new XWPFDocument(new ByteArrayInputStream(service.toDocx(html)))) {
            assertThat(document.getTables()).isEmpty();
            assertThat(document.getParagraphs()).hasSize(3);
            assertThat(document.getParagraphs().get(0).getText()).contains("甲方", "乙方");
            assertThat(document.getParagraphs().get(1).getText())
                    .contains("签名：___ （盖章）");
            assertThat(document.getParagraphs().get(2).getText())
                    .isEqualTo("签署日期：___年___月___日");

            var first = document.getParagraphs().get(0);
            assertThat(first.getSpacingBetween()).isEqualTo(2.0);
            assertThat(first.getSpacingAfter()).isEqualTo(300);
            assertThat(first.getCTP().getPPr().getTabs().sizeOfTabArray()).isGreaterThan(0);
            assertThat(first.getCTP().getPPr().getTabs().getTabArray(0).getPos())
                    .isEqualTo(java.math.BigInteger.valueOf(4150));
            assertThat(first.getRuns().get(0).getFontFamily()).isEqualTo("FangSong");
            assertThat(first.getRuns().get(0).getFontSize()).isEqualTo(14);
        }
    }

    @Test
    void doesNotForceOcrPageBreaksForFlowingEditorHtml() throws Exception {
        String html = """
                <div class="document-page"><p data-doc-style="BODY">第一页</p></div>
                <div class="document-page"><p data-doc-style="BODY">第二页</p></div>
                """;

        try (XWPFDocument document =
                     new XWPFDocument(new ByteArrayInputStream(service.toDocx(html)))) {
            assertThat(document.getParagraphs()).hasSizeGreaterThanOrEqualTo(2);
            boolean hasPageBreak = document.getParagraphs().stream()
                    .flatMap(paragraph -> paragraph.getRuns().stream())
                    .anyMatch(run -> run.getCTR().getBrList().stream()
                            .anyMatch(br -> br.getType() != null
                                    && "page".equals(br.getType().toString())));
            assertThat(hasPageBreak).isFalse();
        }
    }

    @Test
    void exportsEditableTableAndBorderlessSignatureTable() throws Exception {
        String html = """
                <table><tr><td>名称</td><td>金额</td></tr></table>
                <table class="signature-table"><tr><td>甲方（盖章）：</td><td>乙方（盖章）：</td></tr></table>
                """;

        try (XWPFDocument document =
                     new XWPFDocument(new ByteArrayInputStream(service.toDocx(html)))) {
            assertThat(document.getTables()).hasSize(2);
            assertThat(document.getTables().get(0).getRow(0).getCell(0).getText())
                    .contains("名称");
            assertThat(document.getTables().get(1).getRow(0).getCell(1).getText())
                    .contains("乙方");
            assertThat(document.getTables().get(1).getCTTbl().getTblPr()
                    .getTblBorders().getTop().getVal().toString()).isEqualTo("nil");
        }
    }

    @Test
    void exportsInlineEditorFormattingToSeparateRuns() throws Exception {
        String html = """
                <p style="text-align:center">
                  普通<span style="color:rgb(255, 0, 0);background-color:#ffff00;font-family:SimSun;font-size:16px"><strong><u>彩色</u></strong></span><em>斜体</em>
                </p>
                """;

        try (XWPFDocument document =
                     new XWPFDocument(new ByteArrayInputStream(service.toDocx(html)))) {
            var paragraph = document.getParagraphs().get(0);
            assertThat(paragraph.getAlignment()).isEqualTo(ParagraphAlignment.CENTER);
            assertThat(paragraph.getRuns()).hasSizeGreaterThanOrEqualTo(3);
            var colored = paragraph.getRuns().stream()
                    .filter(run -> run.text().contains("彩色"))
                    .findFirst().orElseThrow();
            assertThat(colored.getColor()).isEqualTo("FF0000");
            assertThat(colored.isBold()).isTrue();
            assertThat(colored.getUnderline().toString()).isEqualTo("SINGLE");
            assertThat(colored.getFontFamily()).isEqualTo("SimSun");
            assertThat(colored.getFontSize()).isEqualTo(12);
            Object fill = colored.getCTR().getRPr().getShdArray(0).getFill();
            String fillHex = fill instanceof byte[] bytes
                    ? java.util.HexFormat.of().withUpperCase().formatHex(bytes)
                    : String.valueOf(fill);
            assertThat(fillHex).isEqualTo("FFFF00");
            assertThat(paragraph.getRuns().stream()
                    .filter(run -> run.text().contains("斜体"))
                    .findFirst().orElseThrow().isItalic()).isTrue();
        }
    }

    @Test
    void exportsContractFillUnderlineSpansAsWordUnderline() throws Exception {
        String html = """
                <p>合同金额：<span class="contract-fill-underline" style="display:inline-block;border-bottom:1px solid currentColor;">5000</span>元</p>
                """;

        try (XWPFDocument document =
                     new XWPFDocument(new ByteArrayInputStream(service.toDocx(html)))) {
            var filled = document.getParagraphs().get(0).getRuns().stream()
                    .filter(run -> run.text().contains("5000"))
                    .findFirst().orElseThrow();
            assertThat(filled.getUnderline().toString()).isEqualTo("SINGLE");
        }
    }
}
