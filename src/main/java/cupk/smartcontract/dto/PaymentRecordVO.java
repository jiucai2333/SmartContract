package cupk.smartcontract.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentRecordVO {
    private Long recordId;
    private Long paymentPlanId;
    private Long contractId;
    private BigDecimal paidAmount;
    private LocalDateTime paidAt;
    private String receiptNo;
    private String notes;
    private LocalDateTime createdAt;
}
