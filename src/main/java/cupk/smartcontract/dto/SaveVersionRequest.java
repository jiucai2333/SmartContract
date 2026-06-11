package cupk.smartcontract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaveVersionRequest(
        @NotNull Long contractId,
        @NotBlank String content,
        String contentHash,
        String saveType
) {}
