package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("risk_report")
public class RiskReport {
    @TableId(value = "report_id", type = IdType.AUTO)
    private Long reportId;
    private Long contractId;
    private Long versionId;
    private String reportNo;
    private String contractType;
    private String partyA;
    private String partyB;
    private String businessScope;
    private String highestRiskLevel;
    private Integer riskCount;
    private Integer highCount;
    private Integer mediumCount;
    private Integer lowCount;
    private String contractText;
    private String summary;
    private String modelName;
    private String reviewStatus;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
