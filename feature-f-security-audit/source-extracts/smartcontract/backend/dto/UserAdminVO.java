package cupk.smartcontract.dto;

public record UserAdminVO(
        Long userId,
        String username,
        Long roleId,
        String roleCode,
        String roleName,
        Long deptId,
        String deptName,
        Integer status
) {
}
