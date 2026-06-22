package cupk.smartcontract.controller;

import cupk.smartcontract.dto.AuthUserVO;
import cupk.smartcontract.dto.RoleVO;
import cupk.smartcontract.dto.UserAdminVO;
import cupk.smartcontract.security.AuditOperation;
import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequireRole({"ADMIN"})
public class AdminController {
    private final AuthService authService;

    public AdminController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/users")
    public List<UserAdminVO> listUsers() {
        return authService.listUsers();
    }

    @PutMapping("/users/{userId}/role")
    @AuditOperation(operation = "ADMIN_UPDATE_ROLE", targetType = "USER", targetIdParameter = "userId")
    public AuthUserVO updateRole(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        String roleCode = body.getOrDefault("roleCode", "USER");
        return authService.updateUserRole(userId, roleCode);
    }

    @GetMapping("/roles")
    public List<RoleVO> listRoles() {
        return authService.listRoles();
    }
}
