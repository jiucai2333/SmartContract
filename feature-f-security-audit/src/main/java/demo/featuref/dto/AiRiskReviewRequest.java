package demo.featuref.dto;

import jakarta.validation.constraints.NotBlank;

public record AiRiskReviewRequest(
        Long contractId,
        String contractType,
        String partyA,
        String partyB,
        String businessScope,
        @NotBlank String contractText
) {
}
