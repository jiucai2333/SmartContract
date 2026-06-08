package cupk.smartcontract.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
    /** 实付总额 */
    private BigDecimal totalPaid;
    /** 逾期天数 — 仅当 status != PAID 且已过到期日时计算 */
    private Long overdueDays;
    /** 约定违约金 = amount * 0.0005 * overdueDays */
    private BigDecimal penaltyAmount;
    /** 责任归属提示 */
    private String responsibilityHint;
    private List<PaymentRecordVO> records;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 日违约金比例 0.05% */
    private static final BigDecimal DAILY_PENALTY_RATE = new BigDecimal("0.0005");

    public void calculateOverdue() {
        if ("PAID".equals(status) || dueDate == null || !LocalDate.now().isAfter(dueDate)) {
            overdueDays = 0L;
            penaltyAmount = BigDecimal.ZERO;
            return;
        }
        overdueDays = ChronoUnit.DAYS.between(dueDate, LocalDate.now());
        penaltyAmount = amount.multiply(DAILY_PENALTY_RATE)
                .multiply(BigDecimal.valueOf(overdueDays))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public void determineResponsibility() {
        if (overdueDays == null || overdueDays <= 0) {
            responsibilityHint = null;
            return;
        }
        if (Boolean.TRUE.equals(prerequisiteConfirmed)) {
            responsibilityHint = "⚠ 甲方延迟支付";
        } else if (prerequisiteDeliverableId != null && !Boolean.TRUE.equals(prerequisiteConfirmed)) {
            responsibilityHint = "⚠ 待人工判断乙方履约责任";
        }
    }

    public static String statusName(String status) {
        return switch (status) {
            case "PENDING" -> "待付";
            case "PAID" -> "已付";
            case "OVERDUE" -> "逾期";
            default -> status;
        };
    }
}
