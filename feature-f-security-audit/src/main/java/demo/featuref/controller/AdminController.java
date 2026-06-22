package demo.featuref.controller;

import demo.featuref.common.Result;
import demo.featuref.common.RoleCode;
import demo.featuref.dto.AuthUserVO;
import demo.featuref.dto.RoleVO;
import demo.featuref.dto.UpdateRoleRequest;
import demo.featuref.security.AuditOperation;
import demo.featuref.security.RequireRole;
import demo.featuref.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequireRole(RoleCode.ADMIN)
public class AdminController {
    private final AuthService authService;

    public AdminController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/users")
    public Result<List<AuthUserVO>> users() {
        return Result.success(authService.listUsers());
    }

    @PutMapping("/users/{userId}/role")
    @AuditOperation(operation = "ADMIN_UPDATE_ROLE", targetType = "USER", targetId = "#userId")
    public Result<AuthUserVO> updateRole(
            @PathVariable("userId") Long userId,
            @Valid @RequestBody UpdateRoleRequest request
    ) {
        return Result.success(authService.updateRole(userId, request.roleCode()));
    }

    @GetMapping("/roles")
    public Result<List<RoleVO>> roles() {
        return Result.success(authService.listRoles());
    }
}
