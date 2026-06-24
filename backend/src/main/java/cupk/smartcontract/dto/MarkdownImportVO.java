package cupk.smartcontract.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Markdown 导入解析结果，包含从 Markdown 文本中提取的合同元数据、正文、条款、风险和表格信息。
 * 用于合同编制域：乙方上传审查、Markdown 导入和合同创建。
 */
public record MarkdownImportVO(
        // === 合同元数据（YAML Front Matter）===
        String contractNo,
        String title,
        String type,
        String partyA,
        String partyB,
        BigDecimal amount,
        LocalDate signDate,
        LocalDate dueDate,
        String riskLevel,
        String status,

        // === 合同正文（Markdown 主体，不含 Front Matter）===
        String content,

        // === 条款边界解析结果 ===
        List<ParsedClause> clauses,

        // === 风险标注提取结果 ===
        List<ParsedRisk> risks,

        // === 表格提取结果 ===
        List<Map<String, String>> deliverables,
        List<Map<String, String>> paymentSchedule,

        // === 导入统计 ===
        int clauseCount,
        int riskCount,
        int deliverableCount,
        int paymentNodeCount
) {

    public record ParsedClause(
            int clauseNumber,
            String title,
            String content,
            String riskAnnotation
    ) {}

    public record ParsedRisk(
            String riskLevel,
            String clauseRef,
            String reason,
            String suggestion,
            String replacement
    ) {}
}
