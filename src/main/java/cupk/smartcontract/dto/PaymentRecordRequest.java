package cupk.smartcontract.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentRecordRequest {
    @NotNull private Long paymentPlanId;
    @NotNull private Long contractId;
    @NotNull private BigDecimal paidAmount;
    @NotNull private LocalDateTime paidAt;
    private String receiptNo;
    private String notes;
}
