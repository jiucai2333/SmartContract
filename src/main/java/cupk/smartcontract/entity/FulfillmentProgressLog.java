package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("fulfillment_progress_log")
public class FulfillmentProgressLog {
    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;
    private Long planId;
    private Long contractId;
    private String operation;
    private String beforeStatus;
    private String afterStatus;
    private String beforeValue;
    private String afterValue;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime operateTime;
    private String remark;
    private String clientIp;
}
