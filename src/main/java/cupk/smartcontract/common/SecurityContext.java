package cupk.smartcontract.common;

import cupk.smartcontract.dto.AuthUserVO;

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
}
