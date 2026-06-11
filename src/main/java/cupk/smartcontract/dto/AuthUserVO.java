package cupk.smartcontract.dto;

/**
 * 认证用户视图对象。
 * 每个用户有且仅有一个角色（roleCode）。
 */
public record AuthUserVO(
        Long userId,
        String username,
        Long deptId,
        String roleCode,
        String dataScope,
        String accessToken,
        String refreshToken,
        Long expiresInSeconds
) {
    public static AuthUserVO of(Long userId, String username, Long deptId,
                                String roleCode, String dataScope) {
        return new AuthUserVO(userId, username, deptId, roleCode, dataScope,
                null, null, null);
    }

    public AuthUserVO withTokens(String accessToken, String refreshToken, Long expiresInSeconds) {
        return new AuthUserVO(userId, username, deptId, roleCode, dataScope,
                accessToken, refreshToken, expiresInSeconds);
    }

    public static AuthUserVO wrongPassword() {
        return new AuthUserVO(-1L, null, null, null, null, null, null, null);
    }
}
