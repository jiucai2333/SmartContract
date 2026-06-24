package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fulfillment_plan")
public class FulfillmentPlan extends BaseAuditEntity {
    @TableId(value = "plan_id", type = IdType.AUTO)
    private Long planId;
    private Long contractId;
    private String milestoneName;
    private LocalDate dueDate;
    private LocalDate actualDate;
    private Long ownerId;
    private String status;
    private String completionNotes;
    @TableField("created_by")
    private String createdBy;
    @TableField("updated_by")
    private String updatedBy;
    @Version
    private Integer version;
}
