package cupk.smartcontract.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensitiveDataMaskerTest {
    private final SensitiveDataMasker masker = new SensitiveDataMasker(new ObjectMapper());

    @Test
    void masksDisplaySensitiveData() {
        String masked = masker.maskForDisplay("phone=13812345678 email=user@example.com Authorization: Bearer abc.def");

        assertThat(masked).contains("138****5678");
        assertThat(masked).contains("u***@example.com");
        assertThat(masked).contains("Bearer ******");
    }

    @Test
    void replacesSensitiveDataBeforeAiCall() {
        String masked = masker.maskForAi("张三 110101199003071234 13812345678 user@example.com 6222021234567890123");

        assertThat(masked).contains("[ID_CARD]");
        assertThat(masked).contains("[MOBILE]");
        assertThat(masked).contains("[EMAIL]");
        assertThat(masked).contains("[BANK_ACCOUNT]");
        masker.assertAiSafe(masked);
    }

    @Test
    void blocksAiCallWhenSensitiveDataRemains() {
        assertThatThrownBy(() -> masker.assertAiSafe("contact user@example.com"))
                .isInstanceOf(IllegalStateException.class);
    }
}
