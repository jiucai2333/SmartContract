package cupk.smartcontract.dto;

import jakarta.validation.constraints.*;

public record LinkAttachmentRequest(
        @NotNull Long contractId
) {
}
