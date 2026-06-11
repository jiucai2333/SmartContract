package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("fulfillment_plan")
public class FulfillmentPlan {
    @TableId(value = "plan_id", type = IdType.AUTO)
    private Long planId;
    private Long contractId;
    private String milestoneName;
    private LocalDate dueDate;
    private LocalDate actualDate;
    private Long ownerId;
    private String status;
    private String completionNotes;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private Integer isDeleted;
    private Integer version;
}
