package demo.featuref.dto;

import demo.featuref.common.RoleCode;

import java.util.Set;

public record MenuItem(
        String id,
        String href,
        String label,
        String group,
        Set<RoleCode> roles
) {
    public boolean allows(RoleCode roleCode) {
        return roles == null || roles.isEmpty() || roles.contains(roleCode);
    }
}
