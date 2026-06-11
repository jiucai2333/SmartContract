package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("file_info")
public class FileInfo {
    @TableId(value = "file_id", type = IdType.AUTO)
    private Long fileId;
    private String objectKey;
    private String fileName;
    private String fileType;
    private Long size;
    private String sha256;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
    @com.baomidou.mybatisplus.annotation.TableField("is_deleted")
    private Integer deleted;
}
