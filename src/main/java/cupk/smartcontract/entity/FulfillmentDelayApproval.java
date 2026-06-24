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
@TableName("fulfillment_delay_approval")
public class FulfillmentDelayApproval extends BaseAuditEntity {
    @TableId(value = "approval_id", type = IdType.AUTO)
    private Long approvalId;
    private Long planId;
    private Long contractId;
    private LocalDate originalDueDate;
    private LocalDate requestedDueDate;
    private String delayReason;
    private String status;
    private Long requesterId;
    private String requesterName;
    private LocalDateTime requestedAt;
    private Long approverId;
    private String approverName;
    private LocalDateTime approvedAt;
    private String rejectReason;
    private String noticeStatus;
}
