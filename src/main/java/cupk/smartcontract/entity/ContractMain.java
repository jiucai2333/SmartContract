package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract_main")
public class ContractMain extends BaseAuditEntity {
    @TableId(value = "contract_id", type = IdType.AUTO)
    private Long contractId;
    private String contractNo;
    private String title;
    private String type;
    private BigDecimal amount;
    private String counterparty;
    private Long deptId;
    private Long ownerId;
    private Long templateId;
    private String status;
    @TableField(exist = false)
    private String riskLevel;
    private LocalDate dueDate;
}
