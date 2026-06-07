package cupk.smartcontract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TemplateStatusRequest(
        @NotNull Long templateId,
        @NotBlank String status
) {}
