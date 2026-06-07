package cupk.smartcontract.controller;

import cupk.smartcontract.common.Result;
import cupk.smartcontract.dto.AuthUserVO;
import cupk.smartcontract.dto.LoginRequest;
import cupk.smartcontract.dto.RegisterRequest;
import cupk.smartcontract.service.AuthService;
import cupk.smartcontract.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final AuthService authService;
    private final TokenService tokenService;

    public UserController(AuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public Result userLogin(@RequestBody LoginRequest request) {
        if (request == null || !StringUtils.hasText(request.username())) {
            return Result.error(400, "用户名不能为空");
        }
        if (!StringUtils.hasText(request.password())) {
            return Result.error(400, "密码不能为空");
        }
        AuthUserVO user = authService.login(request.username().trim(), request.password());
        if (user == null) {
            return Result.error(401, "用户名不存在");
        }
        if (user.userId() != null && user.userId() == -1L) {
            return Result.error(401, "密码错误");
        }
        return Result.success(tokenService.issue(user));
    }

    @PostMapping("/register")
    public Result userRegister(@RequestBody RegisterRequest request) {
        if (request == null || !StringUtils.hasText(request.username())) {
            return Result.error(400, "用户名不能为空");
        }
        if (!StringUtils.hasText(request.password())) {
            return Result.error(400, "密码不能为空");
        }
        return Result.success(tokenService.issue(authService.register(request.username().trim(), request.password())));
    }

    @PostMapping("/logout")
    public Result userLogout() {
        return Result.success("退出成功");
    }

    @GetMapping("/current")
    public Result currentUser(HttpServletRequest request) {
        Object user = request.getAttribute("jwtUser");
        if (user == null) {
            return Result.error(401, "用户未登录");
        }
        return Result.success(user);
    }

    @GetMapping("/notLogin")
    public Result notLogin() {
        return Result.error(401, "请先登录");
    }

    @GetMapping("/noPermission")
    public Result noPermission() {
        return Result.error(403, "无权限访问…");
    }
}
