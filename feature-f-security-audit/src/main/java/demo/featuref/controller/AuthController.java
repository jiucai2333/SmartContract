package demo.featuref.controller;

import demo.featuref.common.Result;
import demo.featuref.dto.AuthUserVO;
import demo.featuref.dto.LoginRequest;
import demo.featuref.dto.RegisterRequest;
import demo.featuref.security.AuditOperation;
import demo.featuref.service.AuthService;
import demo.featuref.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final TokenService tokenService;

    public AuthController(AuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    @AuditOperation(operation = "LOGIN", targetType = "USER")
    public Result<AuthUserVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(tokenService.issue(authService.login(request.username(), request.password())));
    }

    @PostMapping("/register")
    @AuditOperation(operation = "REGISTER", targetType = "USER")
    public Result<AuthUserVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(tokenService.issue(authService.register(request.username(), request.password())));
    }

    @PostMapping("/logout")
    @AuditOperation(operation = "LOGOUT", targetType = "USER", includeRequestBody = false)
    public Result<String> logout() {
        return Result.success("logged out");
    }

    @GetMapping("/current")
    public Result<Object> current(HttpServletRequest request) {
        Object user = request.getAttribute("jwtUser");
        return user == null ? Result.error(401, "please login first") : Result.success(user);
    }

    @GetMapping("/not-login")
    public Result<Object> notLogin() {
        return Result.error(401, "please login first");
    }

    @GetMapping("/no-permission")
    public Result<Object> noPermission() {
        return Result.error(403, "permission denied");
    }
}
