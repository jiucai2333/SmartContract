package cupk.smartcontract.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口角色权限注解
 * 标注在 Controller 方法上，指定访问所需的角色
 * 多个角色为 OR 关系（满足任意一个即可）
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    /**
     * 允许访问的角色编码，如 {"ADMIN", "LEGAL"}
     * 为空表示仅需登录即可访问
     */
    String[] value() default {};
}
