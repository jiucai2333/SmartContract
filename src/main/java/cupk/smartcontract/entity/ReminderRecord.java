package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reminder_record")
public class ReminderRecord extends BaseAuditEntity {
    @TableId(value = "reminder_id", type = IdType.AUTO)
    private Long reminderId;
    private Long planId;
    private Long contractId;
    private String reminderLevel;
    private LocalDate reminderDate;
    private String channel;
    private String receiver;
    private String content;
    private String sendStatus;
    private LocalDateTime sentAt;
}
