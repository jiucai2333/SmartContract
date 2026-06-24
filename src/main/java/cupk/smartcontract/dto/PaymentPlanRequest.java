package cupk.smartcontract.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PaymentPlanRequest {
    private Long paymentPlanId;
    private Long planId;
    @NotNull private Long contractId;
    @NotNull private Integer installmentNo;
    @NotNull private BigDecimal ratio;
    @NotNull private BigDecimal amount;
    @NotNull private LocalDate dueDate;
    private Long prerequisiteDeliverableId;
}
