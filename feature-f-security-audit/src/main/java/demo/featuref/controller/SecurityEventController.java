package demo.featuref.controller;

import demo.featuref.common.Result;
import demo.featuref.common.RoleCode;
import demo.featuref.dto.SecurityEventRequest;
import demo.featuref.model.SecurityAuditEventRecord;
import demo.featuref.security.AuditOperation;
import demo.featuref.security.RequireRole;
import demo.featuref.service.SecurityEventService;
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
    @RequireRole({RoleCode.ADMIN})
    @AuditOperation(operation = "LIST_SECURITY_EVENTS", targetType = "SECURITY_AUDIT_EVENT", includeRequestBody = false)
    public Result<List<SecurityAuditEventRecord>> list() {
        return Result.success(securityEventService.list());
    }

    @PostMapping
    @RequireRole({RoleCode.ADMIN, RoleCode.LEGAL, RoleCode.FINANCE, RoleCode.DEPT_LEADER, RoleCode.EXECUTIVE})
    @AuditOperation(operation = "RECORD_SECURITY_EVENT", targetType = "SECURITY_AUDIT_EVENT", targetId = "#p0.targetId")
    public Result<SecurityAuditEventRecord> record(@Valid @RequestBody SecurityEventRequest request) {
        return Result.success(securityEventService.record(request));
    }
}
