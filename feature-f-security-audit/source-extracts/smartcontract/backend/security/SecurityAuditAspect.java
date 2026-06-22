package cupk.smartcontract.security;

import cupk.smartcontract.common.Result;
import cupk.smartcontract.dto.AuthUserVO;
import cupk.smartcontract.service.SecurityAuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;

@Aspect
@Component
public class SecurityAuditAspect {

    private final SecurityAuditService auditService;

    public SecurityAuditAspect(SecurityAuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(audit)")
    public Object audit(ProceedingJoinPoint joinPoint, AuditOperation audit) throws Throwable {
        Long targetId = findTargetId(joinPoint, audit.targetIdParameter());
        try {
            Object result = joinPoint.proceed();
            Long userId = resolveUserId(result);
            if (targetId == null) {
                targetId = resolveTargetId(result);
            }
            safeRecord(userId, audit.operation(), audit.targetType(), targetId,
                    isFailureResult(result) ? "FAILURE" : "SUCCESS");
            return result;
        } catch (Throwable ex) {
            safeRecord(SecurityContext.userId(), audit.operation(), audit.targetType(), targetId,
                    "FAILURE");
            throw ex;
        }
    }

    private void safeRecord(Long userId, String operation, String targetType,
                            Long targetId, String result) {
        try {
            auditService.record(userId, operation, targetType, targetId, clientIp(), result);
        } catch (RuntimeException ignored) {
            // Auditing must never change the outcome of the business operation.
        }
    }

    private Long findTargetId(ProceedingJoinPoint joinPoint, String parameterName) {
        if (parameterName == null || parameterName.isBlank()) {
            return null;
        }
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < parameters.length; i++) {
            if (parameterName.equals(parameters[i].getName()) && args[i] instanceof Number number) {
                return number.longValue();
            }
        }
        return null;
    }

    private Long resolveUserId(Object result) {
        if (SecurityContext.userId() != null) {
            return SecurityContext.userId();
        }
        Object body = unwrap(result);
        if (body instanceof Result apiResult) {
            body = apiResult.getData();
        }
        return body instanceof AuthUserVO user ? user.userId() : null;
    }

    private Long resolveTargetId(Object result) {
        Object body = unwrap(result);
        if (body instanceof Result apiResult) {
            body = apiResult.getData();
        }
        return readLongProperty(body, "getContractId", "contractId", "getId", "id");
    }

    private Long readLongProperty(Object value, String... methodNames) {
        if (value == null) {
            return null;
        }
        for (String methodName : methodNames) {
            try {
                Object id = value.getClass().getMethod(methodName).invoke(value);
                if (id instanceof Number number) {
                    return number.longValue();
                }
                if (id instanceof String text && !text.isBlank()) {
                    return Long.parseLong(text);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // The audit module must not depend on a business entity type.
            }
        }
        return null;
    }

    private Object unwrap(Object value) {
        return value instanceof ResponseEntity<?> response ? response.getBody() : value;
    }

    private boolean isFailureResult(Object result) {
        if (result instanceof ResponseEntity<?> response) {
            return response.getStatusCode().isError();
        }
        if (result instanceof Result apiResult) {
            return apiResult.getCode() == null || apiResult.getCode() >= 400;
        }
        if (result instanceof Map<?, ?> map) {
            Object code = map.get("code");
            return code instanceof Number number && number.intValue() >= 400;
        }
        return false;
    }

    private String clientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return "unknown";
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp != null && !realIp.isBlank() ? realIp.trim() : request.getRemoteAddr();
    }
}
