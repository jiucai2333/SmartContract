package demo.featuref.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SensitiveDataMasker {
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)");
    private static final Pattern AI_MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<![0-9Xx])(\\d{4})\\d{10,11}([0-9Xx]{4})(?![0-9Xx])");
    private static final Pattern AI_ID_CARD = Pattern.compile(
            "(?<![0-9Xx])(?:\\d{15}|\\d{6}(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx])(?![0-9Xx])");
    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9._%+-])([A-Za-z0-9._%+-]*)(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final Pattern AI_EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern BANK_ACCOUNT = Pattern.compile("(?<!\\d)(\\d{4})\\d{4,22}(\\d{4})(?!\\d)");
    private static final Pattern AI_BANK_ACCOUNT = Pattern.compile("(?<!\\d)\\d{12,30}(?!\\d)");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern API_KEY = Pattern.compile("(?i)(sk-|ak-|api[_-]?key[=:]\\s*)[A-Za-z0-9._~+/=-]{8,}");

    private final ObjectMapper objectMapper;

    public SensitiveDataMasker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String maskText(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String masked = MOBILE.matcher(value).replaceAll("$1****$2");
        masked = ID_CARD.matcher(masked).replaceAll("$1**********$2");
        masked = BANK_ACCOUNT.matcher(masked).replaceAll("$1****$2");
        masked = maskEmails(masked);
        masked = BEARER.matcher(masked).replaceAll("Bearer ******");
        masked = API_KEY.matcher(masked).replaceAll("$1******");
        return masked;
    }

    public String maskForAi(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String masked = AI_ID_CARD.matcher(value).replaceAll("[ID_CARD]");
        masked = AI_MOBILE.matcher(masked).replaceAll("[MOBILE]");
        masked = AI_EMAIL.matcher(masked).replaceAll("[EMAIL]");
        masked = AI_BANK_ACCOUNT.matcher(masked).replaceAll("[BANK_ACCOUNT]");
        masked = BEARER.matcher(masked).replaceAll("Bearer [TOKEN]");
        masked = API_KEY.matcher(masked).replaceAll("$1[SECRET]");
        return masked;
    }

    public String maskForDisplay(String value) {
        return maskText(value);
    }

    public void assertAiSafe(String value) {
        if (containsSensitiveData(value)) {
            throw new IllegalStateException("AI request blocked: sensitive data remains after masking.");
        }
    }

    public boolean containsSensitiveData(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return AI_ID_CARD.matcher(value).find()
                || AI_MOBILE.matcher(value).find()
                || AI_EMAIL.matcher(value).find()
                || AI_BANK_ACCOUNT.matcher(value).find()
                || BEARER.matcher(value).find()
                || API_KEY.matcher(value).find();
    }

    public String maskObject(Object value) {
        if (value == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.valueToTree(value);
            JsonNode masked = maskNode(null, root);
            return objectMapper.writeValueAsString(masked);
        } catch (Exception ignored) {
            return maskText(String.valueOf(value));
        }
    }

    private JsonNode maskNode(String fieldName, JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode copy = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                copy.set(field.getKey(), maskNode(field.getKey(), field.getValue()));
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = objectMapper.createArrayNode();
            node.forEach(item -> copy.add(maskNode(fieldName, item)));
            return copy;
        }
        if (!node.isTextual()) {
            return node;
        }
        String text = node.asText();
        if (isSecretField(fieldName)) {
            return objectMapper.getNodeFactory().textNode("******");
        }
        if (isMobileField(fieldName)) {
            return objectMapper.getNodeFactory().textNode(maskMobile(text));
        }
        if (isIdCardField(fieldName)) {
            return objectMapper.getNodeFactory().textNode(maskIdCard(text));
        }
        if (isBankAccountField(fieldName)) {
            return objectMapper.getNodeFactory().textNode(maskBankAccount(text));
        }
        return objectMapper.getNodeFactory().textNode(maskText(text));
    }

    private boolean isSecretField(String fieldName) {
        String name = normalize(fieldName);
        return name.contains("password")
                || name.equals("pwd")
                || name.contains("token")
                || name.contains("secret")
                || name.contains("authorization")
                || name.contains("apikey")
                || name.endsWith("key");
    }

    private boolean isMobileField(String fieldName) {
        String name = normalize(fieldName);
        return name.contains("phone") || name.contains("mobile") || name.contains("tel");
    }

    private boolean isIdCardField(String fieldName) {
        String name = normalize(fieldName);
        return name.contains("idcard") || name.contains("identity") || name.contains("certno");
    }

    private boolean isBankAccountField(String fieldName) {
        String name = normalize(fieldName);
        return name.contains("bankaccount") || name.contains("accountno") || name.contains("bankcard");
    }

    private String normalize(String fieldName) {
        return fieldName == null ? "" : fieldName.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private String maskMobile(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() == 11) {
            return digits.substring(0, 3) + "****" + digits.substring(7);
        }
        return maskText(value);
    }

    private String maskIdCard(String value) {
        if (value == null || value.length() <= 8) {
            return "******";
        }
        return value.substring(0, 4) + "**********" + value.substring(value.length() - 4);
    }

    private String maskBankAccount(String value) {
        if (value == null || value.length() <= 8) {
            return "******";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    private String maskEmails(String value) {
        Matcher matcher = EMAIL.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1) + "***" + matcher.group(3)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
