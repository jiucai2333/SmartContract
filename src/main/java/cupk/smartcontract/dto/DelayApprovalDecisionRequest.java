package cupk.smartcontract.dto;

public record DelayApprovalDecisionRequest(
        Boolean approved,
        String remark
) {
}
