package demo.featuref.model;

import demo.featuref.common.RoleCode;

public record DemoUser(
        Long userId,
        String username,
        String passwordHash,
        Long deptId,
        RoleCode roleCode,
        boolean enabled
) {
    public DemoUser withRole(RoleCode roleCode) {
        return new DemoUser(userId, username, passwordHash, deptId, roleCode, enabled);
    }
}
