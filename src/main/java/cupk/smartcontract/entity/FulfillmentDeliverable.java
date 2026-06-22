package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fulfillment_deliverable")
public class FulfillmentDeliverable extends BaseAuditEntity {
    @TableId(value = "deliverable_id", type = IdType.AUTO)
    private Long deliverableId;
    private Long planId;
    private Long contractId;
    private String deliverableType;
    private String deliverableName;
    private String stageName;
    private String confirmMethod;
    private String status;
    private Integer confirmed;
    private String confirmStatus;
    private String sourceClause;
    private BigDecimal aiConfidence;
    private Integer aiExtracted;
    private Long fileId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String confirmer;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime confirmedAt;
    private Integer acceptancePassed;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String acceptedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime acceptedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String submittedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime submittedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reviewerName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime reviewedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reviewComment;
    private Integer submissionVersion;
    private String remark;
}
