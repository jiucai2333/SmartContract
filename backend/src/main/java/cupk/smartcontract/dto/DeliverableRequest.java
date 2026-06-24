package cupk.smartcontract.dto;

public record DeliverableRequest(
        Long planId,
        Long contractId,
        String deliverableType,
        String deliverableName,
        String stageName,
        String confirmStatus,
        Boolean confirmed,
        Boolean acceptancePassed,
        String remark
) {
}
