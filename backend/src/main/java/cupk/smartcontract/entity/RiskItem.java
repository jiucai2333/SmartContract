package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("risk_item")
public class RiskItem {
    @TableId(value = "risk_id", type = IdType.AUTO)
    private Long riskId;
    private Long reportId;
    private Long contractId;
    private Long versionId;
    private String clauseRef;
    private String riskType;
    private String riskLevel;
    private String suggestion;
    private String replacement;
    private String reviewStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
