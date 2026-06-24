package cupk.smartcontract.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReminderRecordVO(
        Long reminderId,
        Long planId,
        Long contractId,
        String contractTitle,
        String nodeName,
        String reminderLevel,
        LocalDate reminderDate,
        String channel,
        String receiver,
        String content,
        String sendStatus,
        LocalDateTime sentAt
) {
}
