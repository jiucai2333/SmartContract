package cupk.smartcontract.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FulfillmentDelayApprovalVO(
        Long approvalId,
        Long planId,
        Long contractId,
        String contractNo,
        String contractTitle,
        String nodeName,
        LocalDate originalDueDate,
        LocalDate requestedDueDate,
        String delayReason,
        String status,
        String requesterName,
        LocalDateTime requestedAt,
        String approverName,
        LocalDateTime approvedAt,
        String rejectReason,
        String noticeStatus
) {
}
