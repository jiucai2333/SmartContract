package cupk.smartcontract.dto;

import jakarta.validation.constraints.*;

public record AiRiskReviewRequest(
        @NotBlank String contractText,
        Long contractId,
        Long versionId,
        String contractType,
        String partyA,
        String partyB,
        String businessScope,
        String specialTerms
) {
}
