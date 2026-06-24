package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("deliverable")
public class Deliverable extends BaseAuditEntity {
    @TableId(value = "deliverable_id", type = IdType.AUTO)
    private Long deliverableId;
    private Long planId;
    private Long contractId;
    private String deliverableType;
    private String itemName;
    private String contractStage;
    private Integer isConfirmed;
    private LocalDateTime confirmedAt;
    private String confirmedBy;
    private Integer sortOrder;
}
