package cupk.smartcontract.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("operation_log")
public class OperationLog {
    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;
    private Long userId;
    private String operation;
    private String targetType;
    private Long targetId;
    private String ip;
    private String result;
    private LocalDateTime createdAt;
}
