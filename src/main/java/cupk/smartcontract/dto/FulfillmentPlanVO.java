package cupk.smartcontract.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FulfillmentPlanVO(
        Long planId,
        Long contractId,
        String contractNo,
        String contractTitle,
        String counterparty,
        String nodeName,
        String planType,
        LocalDate dueDate,
        String status,
        Integer progress,
        String ownerName,
        String sourceType,
        String warningLevel,
        Long daysLeft,
        String remark,
        LocalDateTime updatedAt
) {
}
