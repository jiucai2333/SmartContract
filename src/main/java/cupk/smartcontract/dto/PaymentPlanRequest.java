package cupk.smartcontract.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentPlanRequest(
        Long contractId,
        String phaseName,
        BigDecimal percentage,
        BigDecimal plannedAmount,
        LocalDate dueDate,
        String prerequisiteDelivery,
        BigDecimal penaltyRate,
        String status,
        String remark
) {
}
