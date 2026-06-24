package demo.featuref.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensitiveDataMaskerTest {
    private final SensitiveDataMasker masker = new SensitiveDataMasker(new ObjectMapper());

    @Test
    void masksKnownSensitiveFields() {
        String json = masker.maskObject(Map.of(
                "username", "alice",
                "password", "PlainSecret",
                "contactPhone", "13812345678",
                "contactIdCard", "110101199001011234",
                "bankAccount", "6222020202020202020",
                "accessToken", "Bearer abc.def.ghi"
        ));

        assertThat(json).contains("\"username\":\"alice\"");
        assertThat(json).contains("\"password\":\"******\"");
        assertThat(json).contains("\"contactPhone\":\"138****5678\"");
        assertThat(json).contains("\"contactIdCard\":\"1101**********1234\"");
        assertThat(json).contains("\"bankAccount\":\"6222****2020\"");
        assertThat(json).contains("\"accessToken\":\"******\"");
        assertThat(json).doesNotContain("PlainSecret", "13812345678", "110101199001011234", "6222020202020202020", "abc.def.ghi");
    }

    @Test
    void masksSensitivePatternsInsideFreeText() {
        String masked = masker.maskText("call 13812345678, mail alice@example.com, token Bearer abc.def.ghi");

        assertThat(masked).contains("138****5678");
        assertThat(masked).contains("a***@example.com");
        assertThat(masked).contains("Bearer ******");
        assertThat(masked).doesNotContain("13812345678", "alice@example.com", "abc.def.ghi");
    }

    @Test
    void masksAndBlocksAiSensitiveContent() {
        String raw = "身份证110101199001011234 手机13812345678 银行卡6222020202020202020 邮箱alice@example.com";

        String masked = masker.maskForAi(raw);

        assertThat(masked).contains("[ID_CARD]", "[MOBILE]", "[BANK_ACCOUNT]", "[EMAIL]");
        assertThat(masked).doesNotContain("110101199001011234", "13812345678", "6222020202020202020", "alice@example.com");
        assertThatCode(() -> masker.assertAiSafe(masked)).doesNotThrowAnyException();
        assertThatThrownBy(() -> masker.assertAiSafe("仍有手机号 13812345678"))
                .isInstanceOf(IllegalStateException.class);
    }
}
