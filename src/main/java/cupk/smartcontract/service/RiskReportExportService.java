package cupk.smartcontract.service;

import cupk.smartcontract.dto.AiRiskVO;
import cupk.smartcontract.dto.RiskReportVO;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Service
public class RiskReportExportService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ContractManagementService contractService;
    private final WordArchiveService wordArchiveService;

    public RiskReportExportService(ContractManagementService contractService,
                                   WordArchiveService wordArchiveService) {
        this.contractService = contractService;
        this.wordArchiveService = wordArchiveService;
    }

    public ExportFile exportDocx(Long reportId) {
        RiskReportVO report = contractService.getRiskReport(reportId);
        String filename = "风险报告-" + safeName(report.reportNo()) + ".docx";
        try {
            return new ExportFile(filename, wordArchiveService.toDocx(buildHtml(report)));
        } catch (IOException ex) {
            throw new IllegalStateException("风险报告导出失败：" + ex.getMessage(), ex);
        }
    }

    private String buildHtml(RiskReportVO report) {
        StringBuilder html = new StringBuilder();
        html.append("<h1>合同风险审查报告</h1>");
        html.append("<h2>一、报告概览</h2>");
        html.append("<table>");
        row(html, "报告编号", report.reportNo());
        row(html, "最高风险等级", formatRiskLevel(report.highestRiskLevel()));
        row(html, "风险数量", report.riskCount() + " 项");
        row(html, "高/中/低风险", report.highCount() + " / " + report.mediumCount() + " / " + report.lowCount());
        row(html, "合同类型", report.contractType());
        row(html, "甲方", report.partyA());
        row(html, "乙方", report.partyB());
        row(html, "业务范围", report.businessScope());
        row(html, "模型", report.modelName());
        row(html, "审查人", report.createdBy());
        row(html, "审查时间", report.createdAt() == null ? "" : report.createdAt().format(TIME_FORMATTER));
        row(html, "摘要", report.summary());
        html.append("</table>");

        html.append("<h2>二、风险明细</h2>");
        if (report.risks() == null || report.risks().isEmpty()) {
            html.append("<p>本次审查未发现明显风险。</p>");
        } else {
            int index = 1;
            for (AiRiskVO risk : report.risks()) {
                html.append("<h3>").append(index++).append(". ")
                        .append(escape(formatRiskLevel(risk.level())))
                        .append(" - ")
                        .append(escape(risk.clause()))
                        .append("</h3>");
                html.append("<p>风险原因：").append(escape(risk.reason())).append("</p>");
                html.append("<p>修改建议：").append(escape(risk.suggestion())).append("</p>");
            }
        }

        html.append("<h2>三、合同正文</h2>");
        html.append("<p>").append(escape(clip(report.contractText(), 6000))).append("</p>");
        return html.toString();
    }

    private void row(StringBuilder html, String label, Object value) {
        html.append("<tr><td>").append(escape(label)).append("</td><td>")
                .append(escape(Objects.toString(value, ""))).append("</td></tr>");
    }

    private String formatRiskLevel(String level) {
        String normalized = Objects.toString(level, "LOW").toUpperCase();
        if ("HIGH".equals(normalized)) return "高风险";
        if ("MEDIUM".equals(normalized)) return "中风险";
        return "低风险";
    }

    private String safeName(String value) {
        return Objects.toString(value, "未命名报告").replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String clip(String value, int max) {
        String text = Objects.toString(value, "");
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private String escape(Object value) {
        return Objects.toString(value, "")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public record ExportFile(String filename, byte[] bytes) {
        public String encodedFilename() {
            return URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        }
    }
}
