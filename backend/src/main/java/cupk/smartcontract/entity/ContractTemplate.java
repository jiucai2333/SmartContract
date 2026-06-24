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
@TableName("contract_template")
public class ContractTemplate extends BaseAuditEntity {
    @TableId(value = "template_id", type = IdType.AUTO)
    private Long templateId;
    private String templateType;
    private String templateName;
    private String description;
    private Long fileId;
    private String createdBy;
    private String updatedBy;
    @Version
    private Integer version;
    @TableField(exist = false)
    private String promptConfig;
    @TableField(exist = false)
    private String status;
}
