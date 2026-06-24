package cupk.smartcontract.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cupk.smartcontract.config.QwenProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class QwenContractService {

    private final QwenProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public QwenContractService(QwenProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.resolvedTimeoutSeconds()))
                .build();
    }

    public JsonNode extractFields(String contractText) {
        return semanticJson("Extract contract fields without changing the source text.", contractText,
                "{\"fields\":[{\"name\":\"\",\"value\":\"\",\"sourceText\":\"\"}]}");
    }

    public JsonNode extractRequiredFields(String contractText, String contractType) {
        String task = """
                你是合同字段识别助手。只分析用户提供的合同原文，不生成、不改写、不补写合同正文。
                目标：识别合同中每一个需要用户填写、确认或补全的位置，并返回语义化字段列表。
                字段名必须表达业务含义，例如：甲方名称、乙方名称、合同金额小写、合同金额大写、
                服务期限起始日期、服务期限终止日期、签署日期、联系人、联系电话、付款比例、交付地点。
                识别规则：
                1. 只返回合同原文中确实存在占位符、空白、待确认项或明显待补全的位置。
                2. 每一个待填写位置都必须单独返回一个字段；即使字段名相似、占位符相同，也不要合并。
                3. 不要套用固定字段清单，要根据当前合同上下文动态识别。
                4. 字段名不要使用“待填写字段1”“空格”“下划线”“元（大写）”这类非语义名称。
                5. 多个占位符在同一句中时，要结合前后文分别命名。例如：
                   “合同金额：____元（大写）：____” 应返回“合同金额小写”和“合同金额大写”。
                6. placeholderText 尽量返回原文中可直接替换的连续占位片段；如果无法稳定定位，置空。
                7. sourceText 返回包含该字段标签和占位符的最短原文片段；保留原文空格和换行，不要解释。
                8. 可以从上下文可靠推断的值放入 suggestedValue；不能可靠推断时置空，绝不编造。
                9. 正文已经填写完整的内容不要作为待填写字段返回。
                10. requiredLevel 只能是 required、before_submit、optional。
                11. status 只能是 unfilled、filled、ignored。
                12. 只输出 JSON，不输出 Markdown，不输出解释。
                合同类型：%s
                """.formatted(contractType == null ? "" : contractType);
        return semanticJson(task, contractText,
                """
                {"fields":[{"fieldKey":"semanticFieldKey","fieldName":"原文中的字段名称","fieldType":"text",\
                "requiredLevel":"required","placeholderText":"原文中的完整占位片段","suggestedValue":"",\
                "sourceText":"字段附近原文","confidence":0.95,"status":"unfilled"}]}""");
    }

    public JsonNode structureClauses(String contractText) {
        return semanticJson("Structure existing clauses without adding or rewriting clauses.", contractText,
                "{\"clauses\":[{\"title\":\"\",\"text\":\"\",\"level\":1}]}");
    }

    public JsonNode suggestChanges(String contractText) {
        return semanticJson("Suggest drafting improvements. Do not rewrite the contract automatically.",
                contractText,
                "{\"suggestions\":[{\"sourceText\":\"\",\"reason\":\"\",\"suggestion\":\"\"}]}");
    }

    private JsonNode semanticJson(String task, String text, String schema) {
        assertReady();
        if (text == null || text.isBlank()) return objectMapper.createObjectNode();
        String clippedText = clip(text, 30000);
        try {
            Map<String, Object> payload = Map.of(
                    "model", properties.resolvedModel(),
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content",
                                    "You are a contract drafting assistant. Only perform semantic analysis. "
                                            + "Never infer layout, coordinates, bbox, font, alignment or pagination. "
                                            + "Return valid JSON only."),
                            Map.of("role", "user", "content",
                                    task + "\nRequired JSON shape: " + schema
                                            + "\nContract text:\n" + clippedText)
                    )
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.resolvedBaseUrl()))
                    .timeout(Duration.ofSeconds(properties.resolvedTimeoutSeconds()))
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IOException("Qwen HTTP " + response.statusCode());
            }
            String content = objectMapper.readTree(response.body())
                    .path("choices").path(0).path("message").path("content").asText();
            return objectMapper.readTree(cleanJson(content));
        } catch (Exception ex) {
            throw new IllegalStateException("Qwen contract semantic analysis failed: "
                    + ex.getMessage(), ex);
        }
    }

    private void assertReady() {
        if (!properties.enabled() || properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("Qwen is not configured");
        }
    }

    private String cleanJson(String value) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.startsWith("```json")) cleaned = cleaned.substring(7);
        else if (cleaned.startsWith("```")) cleaned = cleaned.substring(3);
        if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
        return cleaned.trim();
    }

    private String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

}
