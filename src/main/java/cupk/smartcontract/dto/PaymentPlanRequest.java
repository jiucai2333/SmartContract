package cupk.smartcontract.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentPlanRequest(
        Long contractId,
        Long fulfillmentPlanId,
        String phaseName,
        BigDecimal percentage,
        BigDecimal plannedAmount,
        LocalDate dueDate,
        String payee,
        String paymentCondition,
        String conditionType,
        String prerequisiteDelivery,
        BigDecimal penaltyRate,
        String status,
        String remark
) {
}
