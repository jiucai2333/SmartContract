package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("contract_version")
public class ContractVersion {
    @TableId(value = "version_id", type = IdType.AUTO)
    private Long versionId;
    private Long contractId;
    private String versionNo;
    private String contentHash;
    private Long fileId;
    @TableField("created_by")
    private String createdBy;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField(exist = false)
    private String content;
}
