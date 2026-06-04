package cupk.smartcontract.dto;

import jakarta.validation.constraints.*;

public record AiRiskReviewRequest(
        @NotBlank String contractText,
        String contractType,
        String partyA,
        String partyB,
        String businessScope,
        String specialTerms
) {
}
