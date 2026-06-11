package cupk.smartcontract.dto;

import jakarta.validation.constraints.NotBlank;

public record TemplateCreateRequest(
        @NotBlank String templateType,
        @NotBlank String templateName,
        String description
) {}
