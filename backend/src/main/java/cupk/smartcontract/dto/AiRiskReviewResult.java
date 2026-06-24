package cupk.smartcontract.dto;

import java.util.List;

public record AiRiskReviewResult(
        Long reportId,
        Long contractId,
        Long versionId,
        String reportNo,
        String highestRiskLevel,
        Integer riskCount,
        List<AiRiskVO> risks
) {
}
