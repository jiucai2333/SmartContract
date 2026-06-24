package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("reminder_record")
public class ReminderRecord {
    @TableId(value = "reminder_id", type = IdType.AUTO)
    private Long reminderId;
    private Long contractId;
    private String reminderType;
    private LocalDateTime sendTime;
    private Long receiverId;
    private String sendStatus;
}
