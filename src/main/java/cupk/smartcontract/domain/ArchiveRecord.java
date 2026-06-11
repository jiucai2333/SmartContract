package cupk.smartcontract.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
}
