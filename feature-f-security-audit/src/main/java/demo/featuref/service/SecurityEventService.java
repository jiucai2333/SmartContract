package demo.featuref.service;

import demo.featuref.common.SecurityEventType;
import demo.featuref.dto.SecurityEventRequest;
import demo.featuref.model.SecurityAuditEventRecord;
import demo.featuref.security.SecurityContext;
import demo.featuref.util.SensitiveDataMasker;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SecurityEventService {
    private final AtomicLong idGenerator = new AtomicLong(3000);
    private final Map<Long, SecurityAuditEventRecord> events = new ConcurrentHashMap<>();
    private final SensitiveDataMasker masker;

    public SecurityEventService(SensitiveDataMasker masker) {
        this.masker = masker;
    }

    public List<SecurityAuditEventRecord> list() {
        return events.values().stream()
                .sorted(Comparator.comparing(SecurityAuditEventRecord::eventId).reversed())
                .toList();
    }

    public SecurityAuditEventRecord record(SecurityEventRequest request) {
        SecurityEventType eventType = SecurityEventType.parse(request.eventType());
        Long eventId = idGenerator.incrementAndGet();
        SecurityAuditEventRecord event = new SecurityAuditEventRecord(
                eventId,
                eventType,
                normalize(request.targetType()),
                normalize(request.targetId()),
                SecurityContext.username(),
                normalize(request.summary()),
                masker.maskForDisplay(request.payload()),
                LocalDateTime.now()
        );
        events.put(eventId, event);
        return event;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
