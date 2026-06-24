package cupk.smartcontract.dto;

import jakarta.validation.constraints.NotBlank;

public record SecurityEventRequest(
        @NotBlank String eventType,
        @NotBlank String targetType,
        String targetId,
        String summary,
        String payload
) {
}
