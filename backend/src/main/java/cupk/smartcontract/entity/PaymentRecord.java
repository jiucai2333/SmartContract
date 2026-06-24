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
@TableName("payment_record")
public class PaymentRecord extends BaseAuditEntity {
    @TableId(value = "record_id", type = IdType.AUTO)
    private Long paymentRecordId;
    private Long paymentPlanId;
    private Long contractId;
    private BigDecimal paidAmount;
    private LocalDate paidDate;
    private String bankSerialNo;
    private String handlerName;
    private Long voucherFileId;
    private String payer;
    private String receiver;
    private String remark;
}
