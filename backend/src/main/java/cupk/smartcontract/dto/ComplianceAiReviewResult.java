package cupk.smartcontract.dto;

import java.util.List;

public record ComplianceAiReviewResult(
        String complianceNotice,
        String sanitizedPrompt,
        String modelName,
        List<String> risks
) {
}
