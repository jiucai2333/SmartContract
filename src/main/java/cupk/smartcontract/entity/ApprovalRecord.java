package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("approval_record")
public class ApprovalRecord {
    @TableId(value = "record_id", type = IdType.AUTO)
    private Long recordId;
    private Long instanceId;
    private String nodeName;
    private Long approverId;
    private String action;
    private String comment;
    private LocalDateTime actionTime;
}
