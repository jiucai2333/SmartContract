package cupk.smartcontract.domain;

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
    private String content;
    private String createdBy;
    private LocalDateTime createdAt;
    @TableField("is_locked")
    private Boolean isLocked;
}
