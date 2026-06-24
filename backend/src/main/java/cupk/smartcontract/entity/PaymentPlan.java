package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payment_plan")
public class PaymentPlan extends BaseAuditEntity {
    @TableId(value = "payment_plan_id", type = IdType.AUTO)
    private Long paymentPlanId;
    private Long contractId;
    private Long fulfillmentPlanId;
    private String phaseName;
    private BigDecimal percentage;
    private BigDecimal plannedAmount;
    private LocalDate dueDate;
    private String payee;
    private String paymentCondition;
    private String conditionType;
    private String conditionStatus;
    private String prerequisiteDelivery;
    private BigDecimal penaltyRate;
    private String status;
    private String remark;
}
