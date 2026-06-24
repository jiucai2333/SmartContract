package cupk.smartcontract.controller;

import cupk.smartcontract.common.Result;
import cupk.smartcontract.dto.SecurityAuditEventRecord;
import cupk.smartcontract.dto.SecurityEventRequest;
import cupk.smartcontract.security.AuditOperation;
import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.service.SecurityEventService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/security-events")
public class SecurityEventController {
    private final SecurityEventService securityEventService;

    public SecurityEventController(SecurityEventService securityEventService) {
        this.securityEventService = securityEventService;
    }

    @GetMapping
    @RequireRole("ADMIN")
    @AuditOperation(operation = "LIST_SECURITY_EVENTS", targetType = "SECURITY_AUDIT_EVENT", includeRequestBody = false)
    public Result list() {
        return Result.success(securityEventService.list());
    }

    @PostMapping
    @RequireRole({"ADMIN", "LEGAL", "FINANCE", "DEPT_LEADER", "EXECUTIVE"})
    @AuditOperation(operation = "RECORD_SECURITY_EVENT", targetType = "SECURITY_AUDIT_EVENT", targetId = "#p0.targetId")
    public Result record(@Valid @RequestBody SecurityEventRequest request) {
        return Result.success(securityEventService.record(request));
    }
}
