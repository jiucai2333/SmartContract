package cupk.smartcontract.dto;

import java.time.LocalDate;

public record FulfillmentPlanRequest(
        Long contractId,
        String nodeName,
        String planType,
        LocalDate dueDate,
        String status,
        Integer progress,
        String ownerName,
        String remark
) {
}
