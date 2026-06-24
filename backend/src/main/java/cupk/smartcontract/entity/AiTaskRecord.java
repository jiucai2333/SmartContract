package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_task_record")
public class AiTaskRecord {
    @TableId(value = "task_id", type = IdType.AUTO)
    private Long taskId;
    private Long contractId;
    private String taskType;
    private String modelName;
    private String promptHash;
    private Integer tokenUsage;
    private String status;
    private String inputSummary;
    private String outputSummary;
    private String errorReason;
    private Long durationMs;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
