package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("approval_instance")
public class Approval extends BaseAuditEntity {
    @TableId(value = "instance_id", type = IdType.AUTO)
    private Long instanceId;
    private Long contractId;
    private String flowType;
    private String currentNode;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    @TableField("created_by")
    private String createdBy;
    @TableField("updated_by")
    private String updatedBy;
    @Version
    private Integer version;
}
