package cupk.smartcontract.dto;

import jakarta.validation.constraints.NotNull;

public record CreateContractFromOcrRequest(
        @NotNull Long attachmentId,
        String title,
        String type,
        String counterparty
) {
}
