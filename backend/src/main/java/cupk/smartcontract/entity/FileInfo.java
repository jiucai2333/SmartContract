package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_info")
public class FileInfo extends BaseAuditEntity {
    @TableId(value = "file_id", type = IdType.AUTO)
    private Long fileId;
    private String objectKey;
    private String fileName;
    private String fileType;
    private Long size;
    private String sha256;
    private String createdBy;
    private String updatedBy;
    @Version
    private Integer version;
}
