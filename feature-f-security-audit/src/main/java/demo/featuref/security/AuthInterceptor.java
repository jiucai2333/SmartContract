package demo.featuref.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.featuref.common.Result;
import demo.featuref.common.RoleCode;
import demo.featuref.dto.AuthUserVO;
import demo.featuref.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AuthInterceptor implements HandlerInterceptor {
    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(TokenService tokenService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!request.getRequestURI().startsWith("/api/")) {
            return true;
        }
        String token = resolveToken(request);
        if (token == null) {
            SecurityContext.clear();
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "please login first");
            return false;
        }
        try {
            Map<String, Object> payload = tokenService.verify(token);
            RoleCode roleCode = RoleCode.parse(String.valueOf(payload.getOrDefault("role", "USER")));
            AuthUserVO authUser = AuthUserVO.of(
                    toLong(payload.get("uid")),
                    String.valueOf(payload.get("sub")),
                    toLong(payload.get("did")),
                    roleCode
            );
            request.setAttribute("jwtUser", authUser);
            SecurityContext.set(authUser);

            if (handler instanceof HandlerMethod handlerMethod && !hasRequiredRole(handlerMethod, roleCode)) {
                SecurityContext.clear();
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "permission denied");
                return false;
            }
            return true;
        } catch (RuntimeException ex) {
            SecurityContext.clear();
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "invalid token");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        SecurityContext.clear();
    }

    private boolean hasRequiredRole(HandlerMethod handlerMethod, RoleCode actualRole) {
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole == null || requireRole.value().length == 0 || actualRole.isAdmin()) {
            return true;
        }
        Set<RoleCode> required = Arrays.stream(requireRole.value()).collect(Collectors.toSet());
        return required.contains(actualRole);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return null;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(status, message)));
    }
}
