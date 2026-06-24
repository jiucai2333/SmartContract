package demo.featuref.common;

import java.util.Arrays;

public enum SecurityEventType {
    CONTRACT_CREATE_OR_UPDATE("Contract create/update"),
    VERSION_RESTORE("Version restore"),
    AI_REVIEW("AI review"),
    APPROVAL_ACTION("Approval action"),
    SIGN_FILE_UPLOAD("Signing file upload"),
    ARCHIVE("Archive"),
    DELIVERY_PAYMENT_CHANGE("Delivery/payment change");

    private final String label;

    SecurityEventType(String label) {
        this.label = label;
    }

    public String label() {
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
