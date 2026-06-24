package cupk.smartcontract.security;

import cupk.smartcontract.dto.AuthUserVO;

/**
 * 线程级安全上下文，存储当前请求的用户信息。
 * 在 AuthInterceptor 中设置，在 Service 层读取。
 */
public final class SecurityContext {

    private static final ThreadLocal<AuthUserVO> CURRENT_USER = new ThreadLocal<>();

    private SecurityContext() {
    }

    public static void set(AuthUserVO user) {
        CURRENT_USER.set(user);
    }

    public static AuthUserVO get() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }

    public static String roleCode() {
        AuthUserVO user = get();
        return user != null ? user.roleCode() : "USER";
    }

    public static String dataScope() {
        AuthUserVO user = get();
        return user != null ? user.dataScope() : "SELF";
    }

    public static Long userId() {
        AuthUserVO user = get();
        return user != null ? user.userId() : null;
    }

    public static Long deptId() {
        AuthUserVO user = get();
        return user != null ? user.deptId() : null;
    }

    public static String username() {
        AuthUserVO user = get();
        return user != null ? user.username() : null;
    }
}
