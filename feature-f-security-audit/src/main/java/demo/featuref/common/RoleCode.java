package demo.featuref.common;

import java.util.Arrays;

public enum RoleCode {
    USER("Regular User", DataScope.SELF),
    DEPT_LEADER("Department Leader", DataScope.DEPT),
    LEGAL("Legal Specialist", DataScope.ALL),
    FINANCE("Finance Specialist", DataScope.ALL),
    EXECUTIVE("Executive", DataScope.ALL),
    ADMIN("System Admin", DataScope.ALL);

    private final String label;
    private final DataScope dataScope;

    RoleCode(String label, DataScope dataScope) {
        this.label = label;
        this.dataScope = dataScope;
    }

    public String label() {
        return label;
    }

    public DataScope dataScope() {
        return dataScope;
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }

    public static RoleCode parse(String value) {
        if (value == null || value.isBlank()) {
            return USER;
        }
        String normalized = value.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(role -> role.name().equals(normalized))
                .findFirst()
                .orElse(USER);
    }
}
