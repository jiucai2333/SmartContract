package demo.featuref.service;

import demo.featuref.dto.PageResult;
import demo.featuref.model.AuditLog;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

@Service
public class AuditLogService {
    private final AtomicLong idGenerator = new AtomicLong();
    private final List<AuditLog> logs = new CopyOnWriteArrayList<>();

    public void record(AuditLog log) {
        logs.add(new AuditLog(
                idGenerator.incrementAndGet(),
                log.userId(),
                log.username(),
                log.roleCode(),
                log.operation(),
                log.targetType(),
                log.targetId(),
                log.method(),
                log.path(),
                log.ip(),
                log.result(),
                log.durationMs(),
                log.detail(),
                log.createdAt() == null ? LocalDateTime.now() : log.createdAt()
        ));
    }

    public PageResult<AuditLog> search(long page, long size, Long userId, String operation, String result) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        Stream<AuditLog> stream = logs.stream()
                .filter(log -> userId == null || userId.equals(log.userId()))
                .filter(log -> !StringUtils.hasText(operation) || operation.equalsIgnoreCase(log.operation()))
                .filter(log -> !StringUtils.hasText(result) || result.equalsIgnoreCase(log.result()));
        List<AuditLog> filtered = stream
                .sorted(Comparator.comparing(AuditLog::createdAt).reversed())
                .toList();
        List<AuditLog> records = filtered.stream()
                .skip((safePage - 1) * safeSize)
                .limit(safeSize)
                .toList();
        return new PageResult<>(safePage, safeSize, filtered.size(), records);
    }

    public String defaultOperation(String method, String path) {
        String normalized = path == null ? "unknown" : path.replaceAll("[^A-Za-z0-9]+", "_");
        return (method == null ? "API" : method.toUpperCase(Locale.ROOT)) + normalized.toUpperCase(Locale.ROOT);
    }
}
