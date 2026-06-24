package cupk.smartcontract.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentPlanVO(
        Long paymentPlanId,
        Long contractId,
        Long fulfillmentPlanId,
        String contractTitle,
        String fulfillmentNodeName,
        String phaseName,
        BigDecimal percentage,
        BigDecimal plannedAmount,
        BigDecimal paidAmount,
        BigDecimal unpaidAmount,
        LocalDate dueDate,
        String payee,
        String paymentCondition,
        String conditionType,
        String conditionStatus,
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
