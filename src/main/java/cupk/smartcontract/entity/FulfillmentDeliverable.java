package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fulfillment_deliverable")
public class FulfillmentDeliverable extends BaseAuditEntity {
    @TableId(value = "deliverable_id", type = IdType.AUTO)
    private Long deliverableId;
    private Long contractId;
    private String deliverableType;
    private String deliverableName;
    private String stageName;
    private String confirmMethod;
    private Integer confirmed;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String confirmer;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime confirmedAt;
    private String remark;
}
