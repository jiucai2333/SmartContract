package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract_attachment")
public class ContractAttachment extends BaseAuditEntity {
    @TableId(value = "attachment_id", type = IdType.AUTO)
    private Long attachmentId;
    private Long contractId;
    private Long fileId;
    private String attachType;
    private String ocrStatus;
    private String ocrText;
    @TableField(exist = false)
    private String ocrResult;
    private String ocrError;
    private Integer pageCount;
    private String createdBy;
    private String updatedBy;
    @Version
    private Integer version;
}
