package demo.featuref.controller;

import demo.featuref.common.Result;
import demo.featuref.common.RoleCode;
import demo.featuref.dto.PageResult;
import demo.featuref.model.AuditLog;
import demo.featuref.security.RequireRole;
import demo.featuref.service.AuditLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequireRole(RoleCode.ADMIN)
public class AuditLogController {
    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public Result<PageResult<AuditLog>> search(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String result
    ) {
        return Result.success(auditLogService.search(page, size, userId, operation, result));
    }
}
