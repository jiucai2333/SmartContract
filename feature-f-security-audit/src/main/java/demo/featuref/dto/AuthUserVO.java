package demo.featuref.dto;

import demo.featuref.common.DataScope;
import demo.featuref.common.RoleCode;

public record AuthUserVO(
        Long userId,
        String username,
        Long deptId,
        RoleCode roleCode,
        DataScope dataScope,
        String accessToken,
        String refreshToken,
        Long expiresInSeconds
) {
    public static AuthUserVO of(Long userId, String username, Long deptId, RoleCode roleCode) {
        return new AuthUserVO(
                userId,
                username,
                deptId,
                roleCode,
                roleCode.dataScope(),
                null,
                null,
                null
        );
    }

    public AuthUserVO withTokens(String accessToken, String refreshToken, Long expiresInSeconds) {
        return new AuthUserVO(
                userId,
                username,
                deptId,
                roleCode,
                dataScope,
                accessToken,
                refreshToken,
                expiresInSeconds
        );
    }
}
