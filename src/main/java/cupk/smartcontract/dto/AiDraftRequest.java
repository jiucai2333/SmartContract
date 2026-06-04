package cupk.smartcontract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AiDraftRequest(
        @NotBlank String contractType,
        @NotBlank String partyA,
        @NotBlank String partyB,
        @NotBlank String businessScope,
        @NotNull BigDecimal amount,
        String specialTerms,
        Long attachmentId,
        String ocrReferenceText,
        Long templateId
) {
}
