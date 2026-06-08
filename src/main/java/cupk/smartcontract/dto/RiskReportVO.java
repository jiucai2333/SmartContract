package cupk.smartcontract.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RiskReportVO(
        Long reportId,
        Long contractId,
        Long versionId,
        String reportNo,
        String contractType,
        String partyA,
        String partyB,
        String businessScope,
        String highestRiskLevel,
        Integer riskCount,
        Integer highCount,
        Integer mediumCount,
        Integer lowCount,
        String contractText,
        String summary,
        String modelName,
        String reviewStatus,
        String createdBy,
        LocalDateTime createdAt,
        List<AiRiskVO> risks
) {
}
