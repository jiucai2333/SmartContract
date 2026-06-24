package demo.featuref.model;

import java.time.LocalDateTime;

public record AuditLog(
        Long logId,
        Long userId,
        String username,
        String roleCode,
        String operation,
        String targetType,
        String targetId,
        String method,
        String path,
        String ip,
        String result,
        long durationMs,
        String detail,
        LocalDateTime createdAt
) {
}
