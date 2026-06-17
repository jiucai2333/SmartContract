package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
    private String confirmer;
    private LocalDateTime confirmedAt;
    private String remark;
}
