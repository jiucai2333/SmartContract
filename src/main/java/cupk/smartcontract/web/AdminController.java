package cupk.smartcontract.web;

import cupk.smartcontract.common.RequireRole;
import cupk.smartcontract.dto.AuthUserVO;
import cupk.smartcontract.dto.RoleVO;
import cupk.smartcontract.mapper.UserInfoMapper;
import cupk.smartcontract.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequireRole({"ADMIN"})
public class AdminController {

    private final AuthService authService;
    private final UserInfoMapper userInfoMapper;

    public AdminController(AuthService authService, UserInfoMapper userInfoMapper) {
        this.authService = authService;
        this.userInfoMapper = userInfoMapper;
    }

    @GetMapping("/users")
    public List<AuthUserVO> listUsers() {
        return authService.listUsers();
    }

    @PutMapping("/users/{userId}/role")
    public AuthUserVO updateRole(@PathVariable Long userId,
                                  @RequestBody Map<String, String> body) {
        String roleCode = body.getOrDefault("roleCode", "USER");
        return authService.updateUserRole(userId, roleCode);
    }

    @GetMapping("/roles")
    public List<RoleVO> listRoles() {
        return userInfoMapper.selectAllRoles();
    }
}
