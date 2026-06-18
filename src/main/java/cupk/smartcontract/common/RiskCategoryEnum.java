package cupk.smartcontract.common;

import java.util.Arrays;

public enum RiskCategoryEnum {
    LEGAL_COMPLIANCE("LEGAL_COMPLIANCE", "\u6cd5\u5f8b\u5408\u89c4\u98ce\u9669"),
    PERFORMANCE_DELIVERY("PERFORMANCE_DELIVERY", "\u5c65\u7ea6\u4ea4\u4ed8\u98ce\u9669"),
    PAYMENT_SETTLEMENT("PAYMENT_SETTLEMENT", "\u4ed8\u6b3e\u7ed3\u7b97\u98ce\u9669"),
    IP_CONFIDENTIALITY("IP_CONFIDENTIALITY", "\u77e5\u8bc6\u4ea7\u6743\u4e0e\u4fdd\u5bc6\u98ce\u9669"),
    LIABILITY_APPROVAL("LIABILITY_APPROVAL", "\u8fdd\u7ea6\u8d23\u4efb\u4e0e\u5ba1\u6279\u98ce\u9669");

    private final String code;
    private final String label;

    RiskCategoryEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static RiskCategoryEnum fromCode(String value) {
        if (value == null || value.isBlank()) {
            return LEGAL_COMPLIANCE;
        }
        String normalized = value.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(category -> category.code.equals(normalized))
                .findFirst()
                .orElseGet(() -> inferFromLegacyValue(normalized));
    }

    private static RiskCategoryEnum inferFromLegacyValue(String value) {
        if (value.contains("PAYMENT") || value.contains("\u4ed8\u6b3e") || value.contains("\u7ed3\u7b97")) {
            return PAYMENT_SETTLEMENT;
        }
        if (value.contains("PERFORMANCE") || value.contains("DELIVERY")
                || value.contains("\u5c65\u7ea6") || value.contains("\u4ea4\u4ed8") || value.contains("\u9a8c\u6536")) {
            return PERFORMANCE_DELIVERY;
        }
        if (value.contains("IP") || value.contains("CONFIDENTIAL")
                || value.contains("\u77e5\u8bc6\u4ea7\u6743") || value.contains("\u4fdd\u5bc6")) {
            return IP_CONFIDENTIALITY;
        }
        if (value.contains("LIABILITY") || value.contains("APPROVAL")
                || value.contains("\u8fdd\u7ea6") || value.contains("\u5ba1\u6279")) {
            return LIABILITY_APPROVAL;
        }
        return LEGAL_COMPLIANCE;
    }
}
