package demo.featuref.security;

import demo.featuref.common.DataScope;
import demo.featuref.common.RoleCode;
import demo.featuref.dto.AuthUserVO;

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

    public static Long userId() {
        AuthUserVO user = get();
        return user == null ? null : user.userId();
    }

    public static String username() {
        AuthUserVO user = get();
        return user == null ? null : user.username();
    }

    public static RoleCode roleCode() {
        AuthUserVO user = get();
        return user == null ? RoleCode.USER : user.roleCode();
    }

    public static DataScope dataScope() {
        AuthUserVO user = get();
        return user == null ? DataScope.SELF : user.dataScope();
    }
}
