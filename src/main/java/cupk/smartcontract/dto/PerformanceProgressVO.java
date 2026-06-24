package cupk.smartcontract.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PerformanceProgressVO {
    private long totalDeliverables;
    private long confirmedDeliverables;
    private int deliveryProgress;
    private long totalPaymentPlans;
    private long paidPlans;
    private long overduePlans;
    private BigDecimal totalAmount;
    private BigDecimal totalPaid;
    private BigDecimal totalUnpaid;
}
