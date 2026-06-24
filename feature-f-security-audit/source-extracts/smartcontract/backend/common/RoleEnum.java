package cupk.smartcontract.common;

public enum RoleEnum {

    USER("USER", "普通员工", "SELF"),
    DEPT_LEADER("DEPT_LEADER", "部门主管", "DEPT"),
    LEGAL("LEGAL", "法务专员", "ALL"),
    FINANCE("FINANCE", "财务专员", "ALL"),
    EXECUTIVE("EXECUTIVE", "企业高管", "ALL"),
    ADMIN("ADMIN", "系统管理员", "ALL");

    private final String roleCode;
    private final String roleName;
    private final String dataScope;

    RoleEnum(String roleCode, String roleName, String dataScope) {
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.dataScope = dataScope;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getDataScope() {
        return dataScope;
    }

    public static RoleEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (RoleEnum r : values()) {
            if (r.roleCode.equals(normalized)) {
                return r;
            }
        }
        return null;
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }
}
