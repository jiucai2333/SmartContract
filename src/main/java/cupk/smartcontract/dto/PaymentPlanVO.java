package cupk.smartcontract.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PaymentPlanVO {
    private Long paymentPlanId;
    private Long planId;
    private Long contractId;
    private Integer installmentNo;
    private BigDecimal ratio;
    private BigDecimal amount;
    private LocalDate dueDate;
    private String status;
    private Long prerequisiteDeliverableId;
    private String prerequisiteDeliverableName;
    private Boolean prerequisiteConfirmed;
    private BigDecimal totalPaid;
    private Long overdueDays;
    private BigDecimal penaltyAmount;
    private String responsibilityHint;
    private List<PaymentRecordVO> records;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
