package cupk.smartcontract.common;

import java.util.Arrays;

public enum SecurityEventType {
    CONTRACT_CREATE_OR_UPDATE("合同创建/更新"),
    VERSION_RESTORE("版本恢复"),
    AI_REVIEW("AI 审查"),
    APPROVAL_ACTION("审批动作"),
    SIGN_FILE_UPLOAD("签署文件上传"),
    ARCHIVE("归档"),
    DELIVERY_PAYMENT_CHANGE("履约/付款变更");

    private final String label;

    SecurityEventType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static SecurityEventType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        String normalized = value.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(type -> type.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported security event type: " + value));
    }
}
