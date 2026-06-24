package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fulfillment_voucher")
public class FulfillmentVoucher extends BaseAuditEntity {
    @TableId(value = "voucher_id", type = IdType.AUTO)
    private Long voucherId;
    private Long planId;
    private Long contractId;
    private Long fileId;
    private String voucherType;
    private String reviewStatus;
    private Long reviewerId;
    private String reviewerName;
    private LocalDateTime reviewedAt;
    private Long uploadedBy;
    private String uploadedByName;
    private LocalDateTime uploadedAt;
    private String remark;
}
