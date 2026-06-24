package cupk.smartcontract.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditOperation {
    String operation();

    String targetType();

    String targetIdParameter() default "";

    String targetId() default "";

    boolean includeRequestBody() default true;
}
