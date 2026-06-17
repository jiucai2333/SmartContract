package cupk.smartcontract.dto;

public record DeliverableRequest(
        Long contractId,
        String deliverableType,
        String deliverableName,
        String stageName,
        Boolean confirmed,
        String remark
) {
}
