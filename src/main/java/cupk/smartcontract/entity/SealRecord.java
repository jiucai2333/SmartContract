package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("seal_record")
public class SealRecord extends BaseAuditEntity {
    @TableId(value = "seal_id", type = IdType.AUTO)
    private Long sealId;
    private Long contractId;
    private Long versionId;
    private Long fileId;
    private String fileUrl;
    private String fileName;
    private String sealStatus;
    private LocalDateTime sealTime;
    private Long operatorId;
    private String remark;
    private String signatureProvider;
    private String transactionId;
    private String signatureData;
    private String fileHash;
    private String blockchainHash;
    @TableField("created_by")
    private String createdBy;
    @TableField("updated_by")
    private String updatedBy;
    @Version
    private Integer version;
}
