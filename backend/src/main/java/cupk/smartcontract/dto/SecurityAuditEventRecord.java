package cupk.smartcontract.dto;

import cupk.smartcontract.common.SecurityEventType;

import java.time.LocalDateTime;

public record SecurityAuditEventRecord(
        Long eventId,
        SecurityEventType eventType,
        String targetType,
        String targetId,
        String operator,
        String summary,
        String maskedPayload,
        LocalDateTime recordedAt
) {
}
