package cupk.smartcontract.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentPlanVO(
        Long paymentPlanId,
        Long contractId,
        String contractTitle,
        String phaseName,
        BigDecimal percentage,
        BigDecimal plannedAmount,
        BigDecimal paidAmount,
        BigDecimal unpaidAmount,
        LocalDate dueDate,
        String prerequisiteDelivery,
        Boolean prerequisiteCompleted,
        BigDecimal penaltyRate,
        long overdueDays,
        BigDecimal penaltyAmount,
        String status,
        String responsibilityHint,
        String remark
) {
}
