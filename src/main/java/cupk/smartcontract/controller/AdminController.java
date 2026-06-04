package cupk.smartcontract.controller;

import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.dto.AuthUserVO;
import cupk.smartcontract.dto.RoleVO;
import cupk.smartcontract.service.AuthService;
import org.springframework.web.bind.annotation.*;

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
    public List<AuthUserVO> listUsers() {
        return authService.listUsers();
    }

    /**
     * 设置用户的唯一角色（单角色体系）。
     * Body: { "roleCode": "LEGAL" }
     */
    @PutMapping("/users/{userId}/role")
    public AuthUserVO updateRole(@PathVariable Long userId,
                                  @RequestBody Map<String, String> body) {
        String roleCode = body.getOrDefault("roleCode", "USER");
        return authService.updateUserRole(userId, roleCode);
    }

    @GetMapping("/roles")
    public List<RoleVO> listRoles() {
        return authService.listRoles();
    }
}
