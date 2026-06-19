package cupk.smartcontract.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cupk.smartcontract.config.QwenProperties;
import cupk.smartcontract.dto.AiRiskVO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AiRiskReviewComparisonTest {
    private static final Set<String> FIVE_CATEGORIES = Set.of(
            "SUBJECT_INFO", "PAYMENT", "LIABILITY", "TERM", "DISPUTE_RESOLUTION");

    private final AiDraftService service = new AiDraftService(
            new QwenProperties(null, null, null, null, null, 0, false),
            new ObjectMapper(),
            null,
            null
    );

    @Test
    void standardContractFixtureHasNoExpectedRiskItems() throws Exception {
        String contract = resource("ai-review/standard-software-service-contract.md");
        String result = resource("ai-review/standard-risk-result.json");

        List<AiRiskVO> risks = service.parseRiskItems(result);

        assertThat(contract).contains("统一社会信用代码", "付款", "违约责任", "交付与验收期限", "争议解决");
        assertThat(risks).isEmpty();
    }

    @Test
    void abnormalContractFixtureCoversAllFiveRiskCategories() throws Exception {
        String contract = resource("ai-review/abnormal-software-service-contract.md");
        String result = resource("ai-review/abnormal-risk-result.json");

        List<AiRiskVO> risks = service.parseRiskItems(result);
        Set<String> categories = risks.stream().map(AiRiskVO::category).collect(Collectors.toSet());

        assertThat(contract).contains("后续协商确定", "可根据实际情况顺延", "另行确定处理方式");
        assertThat(categories).containsExactlyInAnyOrderElementsOf(FIVE_CATEGORIES);
        assertThat(risks).allSatisfy(risk -> {
            assertThat(risk.level()).isIn("HIGH", "MEDIUM", "LOW");
            assertThat(risk.category()).isIn(FIVE_CATEGORIES);
            assertThat(risk.clause()).isNotBlank();
            assertThat(risk.reason()).isNotBlank();
            assertThat(risk.suggestion()).isNotBlank();
        });
    }

    @Test
    void qwenPromptRequiresFiveCategoryOutput() throws Exception {
        Field promptField = AiDraftService.class.getDeclaredField("RISK_SYSTEM_PROMPT");
        promptField.setAccessible(true);
        String prompt = (String) promptField.get(null);

        assertThat(prompt).contains(
                "主体信息风险",
                "付款风险",
                "违约风险",
                "期限风险",
                "争议解决风险",
                "\"category\":\"SUBJECT_INFO|PAYMENT|LIABILITY|TERM|DISPUTE_RESOLUTION\""
        );
    }

    private String resource(String path) throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
