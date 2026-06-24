package cupk.smartcontract.dto;

import java.util.Set;

public record MenuItem(
        String id,
        String href,
        String label,
        String group,
        Set<String> roles
) {
    public boolean allows(String roleCode) {
        return roles == null || roles.isEmpty() || roles.contains(roleCode);
    }
}
