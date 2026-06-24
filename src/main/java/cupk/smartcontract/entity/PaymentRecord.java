package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payment_record")
public class PaymentRecord extends BaseAuditEntity {
    @TableId(value = "record_id", type = IdType.AUTO)
    private Long recordId;
    private Long paymentPlanId;
    private Long contractId;
    private BigDecimal paidAmount;
    private LocalDateTime paidAt;
    private String receiptNo;
    private String notes;
}
