package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract_attachment_ocr")
public class ContractAttachmentOcr extends BaseAuditEntity {
    @TableId(value = "ocr_id", type = IdType.AUTO)
    private Long ocrId;
    private Long attachmentId;
    private String ocrStatus;
    private String previewHtml;
    private String plainText;
    private String ocrBlocksJson;
    private String ocrRawJson;
    private String qwenLayoutJson;
    private String parseSource;
    private Boolean approximate = false;
    private String parseWarnings;
    private Integer pageCount;
    private String ocrModel;
    private String qwenModel;
    private Long ocrDurationMs;
    private Long qwenDurationMs;
    private String ocrError;
    private String createdBy;
    private String updatedBy;
    @Version
    private Integer version;
}
