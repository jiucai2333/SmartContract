package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fulfillment_plan")
public class FulfillmentPlan extends BaseAuditEntity {
    @TableId(value = "plan_id", type = IdType.AUTO)
    private Long planId;
    private Long contractId;
    private String nodeName;
    private String planType;
    private LocalDate dueDate;
    private String status;
    private Integer progress;
    private String ownerName;
    private String sourceType;
    private String extractedRule;
    private String remark;
    private LocalDateTime handledAt;

    public String getMilestoneName() {
        return nodeName;
    }

    public void setMilestoneName(String milestoneName) {
        this.nodeName = milestoneName;
    }
}
