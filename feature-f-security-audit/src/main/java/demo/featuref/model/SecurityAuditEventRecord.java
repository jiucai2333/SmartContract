package demo.featuref.model;

import demo.featuref.common.SecurityEventType;

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
