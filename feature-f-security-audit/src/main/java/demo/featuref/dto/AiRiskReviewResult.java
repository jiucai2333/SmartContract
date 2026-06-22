package demo.featuref.dto;

import java.util.List;

public record AiRiskReviewResult(
        String complianceNotice,
        String sanitizedPrompt,
        String modelName,
        List<String> risks
) {
}
