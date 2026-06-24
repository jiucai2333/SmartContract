package demo.featuref.security;

import demo.featuref.common.Result;
import demo.featuref.dto.AuthUserVO;
import demo.featuref.model.AuditLog;
import demo.featuref.model.SecurityAuditEventRecord;
import demo.featuref.service.AuditLogService;
import demo.featuref.util.SensitiveDataMasker;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Aspect
@Component
public class SecurityAuditAspect {
    private final AuditLogService auditLogService;
    private final SensitiveDataMasker masker;
    private final ExpressionParser parser = new SpelExpressionParser();

    public SecurityAuditAspect(AuditLogService auditLogService, SensitiveDataMasker masker) {
        this.auditLogService = auditLogService;
        this.masker = masker;
    }

    @Around("within(@org.springframework.web.bind.annotation.RestController *) && execution(public * *(..))")
    public Object auditEveryControllerOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        AuditOperation audit = resolveAuditAnnotation(joinPoint);
        String targetId = resolveTargetId(joinPoint, audit);
        try {
            Object result = joinPoint.proceed();
            if (targetId == null) {
                targetId = resolveTargetIdFromResult(result);
            }
            record(joinPoint, audit, targetId, resultStatus(result), System.currentTimeMillis() - start, result, null);
            return result;
        } catch (Throwable ex) {
            record(joinPoint, audit, targetId, "FAILURE", System.currentTimeMillis() - start, null, ex);
            throw ex;
        }
    }

    private AuditOperation resolveAuditAnnotation(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        return AnnotationUtils.findAnnotation(method, AuditOperation.class);
    }

    private void record(
            ProceedingJoinPoint joinPoint,
            AuditOperation audit,
            String targetId,
            String result,
            long durationMs,
            Object resultValue,
            Throwable throwable
    ) {
        try {
            HttpServletRequest request = currentRequest();
            String method = request == null ? null : request.getMethod();
            String path = request == null ? null : request.getRequestURI();
            String operation = audit != null && !audit.operation().isBlank()
                    ? audit.operation()
                    : auditLogService.defaultOperation(method, path);
            String targetType = audit != null && !audit.targetType().isBlank()
                    ? audit.targetType()
                    : "API";
            String detail = audit == null || audit.includeRequestBody()
                    ? masker.maskObject(filteredArgs(joinPoint.getArgs()))
                    : null;
            if (throwable != null) {
                detail = masker.maskText((detail == null ? "" : detail + " ") + "error=" + throwable.getMessage());
            }
            AuthUserVO user = resolveAuditUser(resultValue);
            auditLogService.record(new AuditLog(
                    null,
                    user == null ? 0L : user.userId(),
                    user == null ? "anonymous" : user.username(),
                    user == null ? "ANONYMOUS" : user.roleCode().name(),
                    operation,
                    targetType,
                    targetId,
                    method,
                    path,
                    clientIp(request),
                    result,
                    durationMs,
                    detail,
                    LocalDateTime.now()
            ));
        } catch (RuntimeException ignored) {
            // Audit failure must not change the business response.
        }
    }

    private String resultStatus(Object result) {
        if (result instanceof ResponseEntity<?> response) {
            return response.getStatusCode().isError() ? "FAILURE" : "SUCCESS";
        }
        Object body = unwrap(result);
        if (body instanceof Result<?> apiResult) {
            return apiResult.code() == null || apiResult.code() >= 400 ? "FAILURE" : "SUCCESS";
        }
        return "SUCCESS";
    }

    private String resolveTargetId(ProceedingJoinPoint joinPoint, AuditOperation audit) {
        if (audit == null || audit.targetId().isBlank()) {
            return null;
        }
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            StandardEvaluationContext context = new StandardEvaluationContext();
            Object[] args = joinPoint.getArgs();
            String[] parameterNames = signature.getParameterNames();
            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < args.length; i++) {
                context.setVariable("p" + i, args[i]);
                context.setVariable("a" + i, args[i]);
                if (parameterNames != null && i < parameterNames.length) {
                    context.setVariable(parameterNames[i], args[i]);
                }
                if (i < parameters.length) {
                    PathVariable pathVariable = parameters[i].getAnnotation(PathVariable.class);
                    if (pathVariable != null) {
                        String name = !pathVariable.name().isBlank() ? pathVariable.name() : pathVariable.value();
                        if (!name.isBlank()) {
                            context.setVariable(name, args[i]);
                        }
                    }
                }
            }
            Object value = parser.parseExpression(audit.targetId()).getValue(context);
            return value == null ? null : String.valueOf(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String resolveTargetIdFromResult(Object result) {
        Object body = unwrap(result);
        if (body instanceof Result<?> apiResult) {
            body = apiResult.data();
        }
        if (body instanceof SecurityAuditEventRecord event) {
            return event.targetId().isBlank() ? String.valueOf(event.eventId()) : event.targetId();
        }
        if (body instanceof AuthUserVO user) {
            return String.valueOf(user.userId());
        }
        return null;
    }

    private AuthUserVO resolveAuditUser(Object result) {
        AuthUserVO current = SecurityContext.get();
        if (current != null) {
            return current;
        }
        Object body = unwrap(result);
        if (body instanceof Result<?> apiResult) {
            body = apiResult.data();
        }
        return body instanceof AuthUserVO user ? user : null;
    }

    private Object unwrap(Object value) {
        return value instanceof ResponseEntity<?> response ? response.getBody() : value;
    }

    private List<Object> filteredArgs(Object[] args) {
        List<Object> filtered = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof ServletRequest || arg instanceof ServletResponse || arg instanceof BindingResult) {
                continue;
            }
            filtered.add(arg);
        }
        return filtered;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp != null && !realIp.isBlank() ? realIp.trim() : request.getRemoteAddr();
    }
}
