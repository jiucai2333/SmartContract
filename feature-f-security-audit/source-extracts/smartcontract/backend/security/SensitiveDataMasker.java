package cupk.smartcontract.security;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class SensitiveDataMasker {
    private static final Pattern ID_CARD = Pattern.compile(
            "(?<![0-9Xx])(?:\\d{15}|\\d{6}(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx])(?![0-9Xx])");
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern BANK_ACCOUNT = Pattern.compile("(?<!\\d)\\d{12,30}(?!\\d)");

    public String maskForAi(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String masked = ID_CARD.matcher(text).replaceAll("[ID_CARD]");
        masked = MOBILE.matcher(masked).replaceAll("[MOBILE]");
        masked = EMAIL.matcher(masked).replaceAll("[EMAIL]");
        masked = BANK_ACCOUNT.matcher(masked).replaceAll("[BANK_ACCOUNT]");
        return masked;
    }

    public String maskForDisplay(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String masked = ID_CARD.matcher(text).replaceAll(match -> {
            String value = match.group();
            return value.substring(0, Math.min(4, value.length())) + "**********"
                    + value.substring(Math.max(value.length() - 4, 0));
        });
        masked = MOBILE.matcher(masked).replaceAll(match -> {
            String value = match.group();
            return value.substring(0, 3) + "****" + value.substring(7);
        });
        masked = EMAIL.matcher(masked).replaceAll(match -> {
            String value = match.group();
            int at = value.indexOf('@');
            return at <= 1 ? "***" + value.substring(at) : value.charAt(0) + "***" + value.substring(at);
        });
        masked = BANK_ACCOUNT.matcher(masked).replaceAll(match -> {
            String value = match.group();
            return value.substring(0, Math.min(4, value.length())) + "****"
                    + value.substring(Math.max(value.length() - 4, 0));
        });
        return masked;
    }

    public void assertAiSafe(String text) {
        if (containsSensitiveData(text)) {
            throw new IllegalStateException("AI request blocked: sensitive data remains after masking.");
        }
    }

    public boolean containsSensitiveData(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return ID_CARD.matcher(text).find()
                || MOBILE.matcher(text).find()
                || EMAIL.matcher(text).find()
                || BANK_ACCOUNT.matcher(text).find();
    }
}
