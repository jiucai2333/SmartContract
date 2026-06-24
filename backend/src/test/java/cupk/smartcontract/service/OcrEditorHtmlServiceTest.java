package cupk.smartcontract.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cupk.smartcontract.dto.OcrPipelineVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OcrEditorHtmlServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OcrEditorHtmlService service = new OcrEditorHtmlService(objectMapper);

    @Test
    void buildsUnifiedEditableContractHtmlAndFiltersPageNumbers() throws Exception {
        OcrPipelineVO document = document(
                block("title", "采购合同", 400, 60, 800, 120, 1, null),
                block("paragraph", "一、合同标的", 120, 180, 1000, 230, 2, null),
                block("paragraph", "本合同采购办公用品。", 160, 250, 1000, 310, 3, null),
                block("page_number", "1", 580, 1600, 610, 1640, 4, null)
        );

        String html = service.buildEditableHtml(objectMapper.writeValueAsString(document));

        assertThat(html).contains(
                "<h1 data-doc-style=\"TITLE\"",
                "font-size:18pt",
                "<h2 class=\"clause-title\"",
                "data-indent-chars=\"0\"",
                "data-doc-style=\"CLAUSE_BODY\"",
                "data-indent-chars=\"2\"",
                "text-indent:2em");
        assertThat(html).doesNotContain(">1<", "position:absolute");
        assertThat(html.indexOf("采购合同")).isLessThan(html.indexOf("一、合同标的"));
        assertThat(html.indexOf("一、合同标的")).isLessThan(html.indexOf("本合同采购"));
    }

    @Test
    void keepsStructuredTableEditableAndFallsBackToBodyForInvalidTable() throws Exception {
        Map<String, Object> table = Map.of("rows", List.of(
                Map.of("cells", List.of(Map.of("text", "名称"), Map.of("text", "金额"))),
                Map.of("cells", List.of(Map.of("text", "服务费"), Map.of("text", "1000")))
        ));
        OcrPipelineVO document = document(
                block("table", "名称 金额", 100, 200, 1000, 400, 1, table),
                block("table", "无法识别的表格内容", 100, 450, 1000, 520, 2, Map.of())
        );

        String html = service.buildEditableHtml(objectMapper.writeValueAsString(document));

        assertThat(html).contains("<table", "<td", "名称", "1000");
        assertThat(html).contains("data-doc-style=\"CLAUSE_BODY\"", "无法识别的表格内容");
        assertThat(html).doesNotContain("<img");
    }

    @Test
    void pairsLeftAndRightSignatureBlocksIntoEditableParagraph() throws Exception {
        OcrPipelineVO document = document(
                block("paragraph", "甲方（盖章）：", 100, 900, 500, 960, 1, null),
                block("paragraph", "乙方（盖章）：", 650, 900, 1050, 960, 2, null)
        );

        String html = service.buildEditableHtml(objectMapper.writeValueAsString(document));

        assertThat(html).contains(
                "class=\"signature-block\"",
                "class=\"signature-row\"",
                "style=\"margin-top:48px;text-indent:0;\"",
                "grid-template-columns:1fr 1fr",
                "margin-bottom:20px",
                "line-height:2",
                "white-space:pre-wrap;text-indent:0;",
                "甲方（盖章）：",
                "乙方（盖章）：");
        assertThat(html).doesNotContain("<table");
    }

    @Test
    void cleansDuplicatesMarkdownAndEscapesHtml() throws Exception {
        OcrPipelineVO document = document(
                block("paragraph", "## <script>合同</script>", 100, 200, 900, 250, 1, null),
                block("paragraph", "<script>合同</script>", 100, 260, 900, 310, 2, null)
        );

        String html = service.buildEditableHtml(objectMapper.writeValueAsString(document));

        assertThat(html).contains("&lt;script&gt;合同&lt;/script&gt;");
        assertThat(html).doesNotContain("<script>");
        assertThat(html.split("&lt;script&gt;", -1)).hasSize(2);
    }

    @Test
    void plainTextFallbackUsesSameClauseAndBodyRulesAndDropsPageNumberLines() {
        String html = service.buildPlainTextHtml("一、合同标的\n正文内容\n1");

        assertThat(html).contains("class=\"clause-title\"", "data-indent-chars=\"2\"");
        assertThat(html).doesNotContain(">1<");
    }

    @Test
    void plainTextPdfContentUsesContractLayoutAndMergesWrappedLines() {
        String text = """
                广告服务合同
                甲方（委托方/广告主）：____________________________
                乙方（服务方/广告服务提供方）：____________________
                住所地：____________________________
                联系人：____________________
                联系电话：____________________
                根据《中华人民共和国民法典》《中华人民共和国广告法》及相关法
                律法规，甲、乙双方在平等、自愿、诚实信用的基础上，就乙方向甲方提
                供广告服务事宜达成如下协议。
                第一条 服务内容
                1. 乙方接受甲方委托，提供广告策划、创意设计、文案撰写、素材制
                作、媒介投放、数据监测、投放优化及结案报告等服务。
                2. 具体服务范围、投放平台、媒介渠道、发布形式、投放周期、预算
                安排及交付要求，以本合同附件、排期表、报价单或双方书面确认文件为
                准。
                甲方负责人（或授权代表） 乙方负责人（或授权代表）
                签名： （盖章） 签名： （盖章）
                签署日期： 年 月 日 签署日期： 年 月 日
                """;

        String html = service.buildPlainTextHtml(text);

        assertThat(html).contains(
                "<h1 data-doc-style=\"TITLE\"",
                ">广告服务合同</h1>",
                "<p class=\"party-info\" data-doc-style=\"FIELD_LINE\" data-indent-chars=\"0\"",
                "乙方（服务方/广告服务提供方）：____________________",
                "<h2 class=\"clause-title\"",
                "第一条 服务内容",
                "根据《中华人民共和国民法典》《中华人民共和国广告法》及相关法律法规",
                "素材制作、媒介投放",
                "确认文件为准。",
                "class=\"signature-row\"",
                "<span style=\"white-space:pre-wrap;text-indent:0;\">甲方负责人（或授权代表）</span>",
                "<span style=\"white-space:pre-wrap;text-indent:0;\">乙方负责人（或授权代表）</span>");
        assertThat(html).doesNotContain("相关法</p>", "素材制</p>", "<table");
    }

    @Test
    void coordinatePartyAndFieldLinesAreNotIndentedAsBodyText() throws Exception {
        OcrPipelineVO document = document(
                block("title", "广告服务合同", 360, 60, 760, 120, 1, null),
                block("paragraph", "甲方（委托方/广告主）：________________", 120, 180, 900, 220, 2, null),
                block("paragraph", "乙方（服务方/广告服务提供方）：________________", 120, 235, 900, 275, 3, null),
                block("paragraph", "住所地：________________", 120, 290, 900, 330, 4, null),
                block("paragraph", "根据双方协商一致，订立本合同。", 160, 360, 900, 410, 5, null)
        );

        String html = service.buildEditableHtml(objectMapper.writeValueAsString(document));

        assertThat(html).contains(
                "甲方（委托方/广告主）：________________",
                "乙方（服务方/广告服务提供方）：________________",
                "住所地：________________",
                "<p class=\"party-info\" data-doc-style=\"FIELD_LINE\" data-indent-chars=\"0\"",
                "<p data-doc-style=\"CLAUSE_BODY\" data-indent-chars=\"2\"");
    }

    @Test
    void clauseWinsOverGenericTitleAndPartyNamesDoNotTurnLongBodyIntoSignature() throws Exception {
        OcrPipelineVO document = document(
                block("title", "一、合同标的", 120, 200, 350, 250, 1, null),
                block("paragraph", "甲方向乙方采购办公用品，乙方应当按约定时间完成交付并承担质量责任。",
                        120, 280, 1050, 360, 2, null)
        );

        String html = service.buildEditableHtml(objectMapper.writeValueAsString(document));

        assertThat(html).contains(
                "<h2 class=\"clause-title\"",
                "data-doc-style=\"CLAUSE_BODY\"",
                "data-indent-chars=\"2\"");
        assertThat(html).doesNotContain(
                "<h1 data-doc-style=\"TITLE\">一、合同标的",
                "class=\"signature-block\">甲方向乙方采购");
    }

    @Test
    void recognizesTopContractNameAsTitleEvenWhenOcrMetadataIsWeak() throws Exception {
        OcrPipelineVO document = document(
                block("paragraph", "顾问咨询合同", 780, 210, 1060, 270, 1, null),
                block("paragraph", "甲方：________", 300, 360, 700, 420, 2, null)
        );

        String html = service.buildEditableHtml(objectMapper.writeValueAsString(document));

        assertThat(html).contains("<h1 data-doc-style=\"TITLE\"");
        assertThat(html).contains(">顾问咨询合同</h1>");
        assertThat(html).doesNotContain("data-doc-style=\"CLAUSE_BODY\">顾问咨询合同");
    }

    @Test
    void numberedSubclausesRemainIndentedBodyParagraphs() throws Exception {
        OcrPipelineVO document = document(
                block("paragraph", "1、本合同执行过程中发生争议。", 170, 500, 1000, 550, 1, null),
                block("paragraph", "2、本合同一式两份。", 170, 570, 1000, 620, 2, null)
        );

        String html = service.buildEditableHtml(objectMapper.writeValueAsString(document));

        assertThat(html).contains("1、本合同", "2、本合同", "data-indent-chars=\"2\"");
        assertThat(html).doesNotContain("class=\"clause-title\"");
    }

    @Test
    void preservesConsecutiveSpacesAsFillBlanks() throws Exception {
        OcrPipelineVO document = document(
                block("paragraph", "甲方：               .", 120, 200, 900, 250, 1, null),
                block("paragraph", "许可期限为              ，许可地点为              。",
                        120, 270, 1050, 320, 2, null)
        );

        String html = service.buildEditableHtml(objectMapper.writeValueAsString(document));
        String plain = service.buildPlainTextHtml("人民币        元\n    年  月  日");

        assertThat(html).contains(
                "甲方：               .",
                "许可期限为              ，许可地点为              。");
        assertThat(plain).contains("人民币        元", "    年  月  日");
    }

    @Test
    void recognizesShortSubClauseHeadingsAsH3() throws Exception {
        OcrPipelineVO document = document(
                block("paragraph", "（一）服务内容", 170, 300, 520, 350, 1, null),
                block("paragraph", "1. 服务方式", 170, 380, 520, 430, 2, null),
                block("paragraph", "2. 服务期限", 170, 460, 520, 510, 3, null),
                block("paragraph", "5.1.1 乙方应按照甲方要求提供技术支持服务。", 170, 540, 1100, 590, 4, null)
        );

        String html = service.buildEditableHtml(objectMapper.writeValueAsString(document));

        assertThat(html).contains(
                "<h3 class=\"sub-clause-title\"",
                "data-doc-style=\"SUB_HEADING\"",
                "（一）服务内容",
                "1. 服务方式",
                "2. 服务期限");
        assertThat(html).contains("<p data-doc-style=\"CLAUSE_BODY\"");
        assertThat(html).contains("5.1.1 乙方应按照甲方要求提供技术支持服务。");
    }

    @Test
    void classifiesDualBankRowsBeforeSignatureAsSignatureRowsOnly() throws Exception {
        OcrPipelineVO document = document(
                block("paragraph", "乙方收款账户如下：", 120, 260, 700, 310, 1, null),
                block("paragraph", "开户银行：＿＿＿＿", 120, 330, 700, 380, 2, null),
                block("paragraph", "账号：＿＿＿＿", 120, 400, 700, 450, 3, null),
                block("paragraph", "开户银行：＿＿＿＿        开户银行：＿＿＿＿", 120, 900, 1050, 950, 4, null),
                block("paragraph", "账号：＿＿＿＿        账号：＿＿＿＿", 120, 970, 1050, 1020, 5, null),
                block("paragraph", "签名：＿＿＿＿        签名：＿＿＿＿", 120, 1040, 1050, 1090, 6, null)
        );

        String html = service.buildEditableHtml(objectMapper.writeValueAsString(document));

        assertThat(html).contains("class=\"signature-row\"");
        assertThat(html).contains(
                "style=\"display:grid;grid-template-columns:1fr 1fr;margin-bottom:20px;line-height:2;text-indent:0;\"",
                "<span style=\"white-space:pre-wrap;text-indent:0;\">开户银行：＿＿＿＿</span>"
                        + "<span style=\"white-space:pre-wrap;text-indent:0;\">开户银行：＿＿＿＿</span>",
                "<span style=\"white-space:pre-wrap;text-indent:0;\">账号：＿＿＿＿</span>"
                        + "<span style=\"white-space:pre-wrap;text-indent:0;\">账号：＿＿＿＿</span>");
        assertThat(html).contains("<p data-doc-style=\"CLAUSE_BODY\" data-indent-chars=\"2\"")
                .contains("乙方收款账户如下：", "开户银行：＿＿＿＿", "账号：＿＿＿＿");
    }

    private OcrPipelineVO document(OcrPipelineVO.Block... blocks) {
        return new OcrPipelineVO("pdf", "paddleocr", true,
                List.of(new OcrPipelineVO.Page(1, 1191, 1684, List.of(blocks))),
                null, List.of());
    }

    private OcrPipelineVO.Block block(String type, String text,
                                      double x0, double y0, double x1, double y1,
                                      int order, Object table) {
        return new OcrPipelineVO.Block(
                "p1_b" + order, type, text, List.of(x0, y0, x1, y1),
                "block_bbox", "unknown", "unknown", null, order,
                "paddleocr", true, table, null);
    }
}
