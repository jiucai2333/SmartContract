package cupk.smartcontract.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class OverdueItemVO {
    private Long paymentPlanId;
    private Long contractId;
    private String contractNo;
    private String contractTitle;
    private Integer installmentNo;
    private BigDecimal ratio;
    private BigDecimal amount;
    private LocalDate dueDate;
    private Long overdueDays;
    private BigDecimal penaltyAmount;
    private String responsibilityHint;
    private String prerequisiteDeliverableName;
    private Boolean prerequisiteConfirmed;
    private BigDecimal totalPaid;
}
