package cupk.smartcontract.dto;

import jakarta.validation.constraints.*;

public record AiRiskReviewRequest(
        @NotBlank String contractText,
        Long contractId,
        Long attachmentId,
        Long versionId,
        String contractType,
        String partyA,
        String partyB,
        String businessScope,
        String specialTerms
) {
}
