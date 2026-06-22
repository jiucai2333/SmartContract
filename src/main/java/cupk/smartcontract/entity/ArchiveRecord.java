package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("archive_record")
public class ArchiveRecord extends BaseAuditEntity {
    @TableId(value = "archive_id", type = IdType.AUTO)
    private Long archiveId;
    private Long contractId;
    private Long versionId;
    private String archiveNo;
    private LocalDateTime archiveTime;
    private Long archiverId;
    private Boolean isLocked;
    private String merkleRoot;
    @TableField("created_by")
    private String createdBy;
    @TableField("updated_by")
    private String updatedBy;
    @Version
    private Integer version;
}
