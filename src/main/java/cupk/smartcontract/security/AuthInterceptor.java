package cupk.smartcontract.security;

import cupk.smartcontract.dto.AuthUserVO;
import cupk.smartcontract.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class AuthInterceptor implements HandlerInterceptor {

    private final TokenService tokenService;

    public AuthInterceptor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!requiresAuth(request)) {
            return true;
        }
        String token = resolveToken(request);
        if (token == null) {
            unauthorized(response);
            return false;
        }
        try {
            Map<String, Object> payload = tokenService.verify(token);
            String username = String.valueOf(payload.get("sub"));
            Long userId = toLong(payload.get("uid"));
            Long deptId = toLong(payload.get("did"));
            String roleCode = String.valueOf(payload.getOrDefault("role", "USER"))
                    .trim().toUpperCase();
            String dataScope = String.valueOf(payload.getOrDefault("scope", "SELF"))
                    .trim().toUpperCase();

            AuthUserVO authUser = AuthUserVO.of(userId, username, deptId, roleCode, dataScope);
            request.setAttribute("jwtUser", authUser);
            SecurityContext.set(authUser);

            if (handler instanceof HandlerMethod hm) {
                RequireRole requireRole = hm.getMethodAnnotation(RequireRole.class);
                if (requireRole == null) {
                    requireRole = hm.getBeanType().getAnnotation(RequireRole.class);
                }
                if (requireRole != null && requireRole.value().length > 0) {
                    if ("ADMIN".equals(roleCode)) {
                        return true;
                    }
                    List<String> required = Arrays.stream(requireRole.value())
                            .map(r -> r.trim().toUpperCase())
                            .toList();
                    if (!required.contains(roleCode)) {
                        forbidden(response);
                        return false;
                    }
                }
            }

            return true;
        } catch (Exception ex) {
            unauthorized(response);
            return false;
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private boolean requiresAuth(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) {
            return false;
        }
        return !uri.equals("/api/user/login")
                && !uri.equals("/api/user/register")
                && !uri.equals("/api/user/notLogin")
                && !uri.equals("/api/user/noPermission");
    }

    private void unauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"请先登录\",\"data\":null}");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        SecurityContext.clear();
    }

    private void forbidden(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"msg\":\"权限不足\",\"data\":null}");
    }
}
