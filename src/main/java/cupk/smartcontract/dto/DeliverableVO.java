package cupk.smartcontract.dto;

import java.time.LocalDateTime;

public record DeliverableVO(
        Long deliverableId,
        Long contractId,
        String contractTitle,
        String deliverableType,
        String deliverableName,
        String stageName,
        String confirmMethod,
        Boolean confirmed,
        String confirmer,
        LocalDateTime confirmedAt,
        String remark
) {
}
