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
    private Long planId;
    private Long contractId;
    private Integer installmentNo;
    private BigDecimal ratio;
    private BigDecimal amount;
    private LocalDate dueDate;
    private String status;
    private Long prerequisiteDeliverableId;
}
