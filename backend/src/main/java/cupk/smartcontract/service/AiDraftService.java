package cupk.smartcontract.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cupk.smartcontract.config.QwenProperties;
import cupk.smartcontract.dto.AiRiskReviewRequest;
import cupk.smartcontract.dto.AiRiskVO;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiDraftService {
    private static final String RISK_SYSTEM_PROMPT = """
            你是一名企业合同法务审查专家，负责识别合同文本中的五类风险。
            请只基于用户提供的合同文本进行分析，不要输出与合同无关的内容。

            风险分类只能使用以下五类 category：
            - SUBJECT_INFO：主体信息风险，审查甲乙方名称、统一社会信用代码、授权代表、地址、联系方式、签署主体是否完整明确。
            - PAYMENT：付款风险，审查金额、付款节点、比例、发票、付款条件、逾期付款责任是否明确。
            - LIABILITY：违约风险，审查违约情形、违约金、损害赔偿、免责条款、责任上限是否合理。
            - TERM：期限风险，审查生效日期、履行期限、交付/验收期限、续期、解除期限、通知期限是否明确。
            - DISPUTE_RESOLUTION：争议解决风险，审查管辖法院、仲裁机构、适用法律、争议处理流程是否明确有效。

            输出要求：
            - 仅返回 JSON，不要包含 Markdown 代码块或解释性文字。
            - JSON 格式为 {"risks":[{"level":"HIGH|MEDIUM|LOW","category":"SUBJECT_INFO|PAYMENT|LIABILITY|TERM|DISPUTE_RESOLUTION","clause":"contract original text","reason":"risk reason","suggestion":"modification guidance for user","replacement":"formal replacement clause text ready for backfill"}]}
            - clause 必须优先返回原文片段，长度控制在 20 到 180 字；不要只返回条款编号。
            - suggestion 是给用户看的修改指引，可以包含操作说明（如在某条款补充、建议改为等）。
            - replacement 是可直接回填的正式合同条款文本，不得包含任何指导性前缀。如果是新增内容，replacement 为完整的条款文本；如果是替换现有文字，replacement 为替换后的完整条款文本。
            - 如果没有发现明显风险，返回 {"risks":[]}。
            - 最多返回 10 条风险，优先返回高风险和影响审批的事项。
            - 每一条风险必须落入上述五类之一，不要自创新分类。
            """;

    private final QwenProperties qwenProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiDraftService(QwenProperties qwenProperties, ObjectMapper objectMapper) {
        this.qwenProperties = qwenProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(qwenProperties.resolvedTimeoutSeconds()))
                .build();
    }

    public List<AiRiskVO> analyzeRisks(AiRiskReviewRequest request) {
        assertQwenReady();
        String contractText = request.contractText();
        if (contractText == null || contractText.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> payload = Map.of(
                    "model", qwenProperties.resolvedModel(),
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", RISK_SYSTEM_PROMPT),
                            Map.of("role", "user", "content", buildRiskUserPrompt(
                                    request.contractText(), buildSanitizedContext(request)))
                    )
            );
            return parseRiskItems(chatCompletion(payload));
        } catch (Exception ex) {
            throw new IllegalStateException("AI risk review failed: " + rootMessage(ex), ex);
        }
    }

    public String modelName() {
        return qwenProperties.resolvedModel();
    }

    public record FulfillmentNode(
            String nodeName,
            String nodeType,
            LocalDate plannedDate,
            String responsibleParty,
            String sourceClause,
            Double confidence,
            boolean aiExtracted,
            boolean dateConfirmed
    ) {
    }

    public List<FulfillmentNode> extractFulfillmentNodes(String contractText, LocalDate contractDueDate) {
        return extractFulfillmentNodes(contractText, contractDueDate, null, null);
    }

    public List<FulfillmentNode> extractFulfillmentNodes(String contractText,
                                                         LocalDate contractDueDate,
                                                         LocalDate signDate,
                                                         LocalDate archiveDate) {
        if (contractText == null || contractText.isBlank()) {
            return List.of();
        }
        assertQwenReady();
        String baseDateContext = "signDate: " + (signDate == null ? "unknown" : signDate)
                + "\narchiveDate: " + (archiveDate == null ? "unknown" : archiveDate)
                + "\ndueDate: " + (contractDueDate == null ? "unknown" : contractDueDate)
                + "\n";
        String prompt = """
                Extract fulfillment tracking nodes from the contract text below.

                Scope:
                1. Payment, delivery, acceptance, warranty, renewal, termination, confidentiality clauses with time requirements.
                2. Each node must include: nodeName, nodeType, plannedDate, responsibleParty, sourceClause, confidence.
                3. plannedDate in yyyy-MM-dd; derive from contract dates where relative; null if indeterminable.
                4. Only extract from the contract text, do not fabricate.

                Output: JSON only, no markdown.
                Format: {"nodes":[{"nodeName":"...","nodeType":"PAYMENT|DELIVERY|ACCEPTANCE|WARRANTY|RENEWAL|TERMINATION|INVOICE|CONFIDENTIALITY|OTHER","plannedDate":"2026-07-01","responsibleParty":"...","sourceClause":"...","confidence":0.9}]}
                Max 12 nodes, sorted by plannedDate ascending.

                dueDate: %s
                Contract text:
                ---
                %s
                ---
                """.formatted(contractDueDate == null ? "unknown" : contractDueDate, clip(baseDateContext + contractText, 12000));
        try {
            Map<String, Object> payload = baseChatPayload(
                    List.of(
                            Map.of("role", "system", "content", "You are a contract fulfillment node extraction assistant. Output structured JSON only."),
                            Map.of("role", "user", "content", prompt)
                    ),
                    false,
                    true
            );
            return parseFulfillmentNodes(chatCompletion(payload));
        } catch (Exception ex) {
            throw new IllegalStateException("AI fulfillment extraction failed: " + rootMessage(ex), ex);
        }
    }

    private String buildSanitizedContext(AiRiskReviewRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.contractType() != null && !request.contractType().isBlank()) {
            sb.append("contractType: ").append(request.contractType()).append("; ");
        }
        if (request.partyA() != null && !request.partyA().isBlank()) {
            sb.append("partyA: [COMPANY_A]; ");
        }
        if (request.partyB() != null && !request.partyB().isBlank()) {
            sb.append("partyB: [COMPANY_B]; ");
        }
        if (request.businessScope() != null && !request.businessScope().isBlank()) {
            sb.append("businessScope: ").append(clip(request.businessScope(), 300)).append("; ");
        }
        if (request.specialTerms() != null && !request.specialTerms().isBlank()) {
            sb.append("specialTerms: ").append(clip(request.specialTerms(), 300)).append("; ");
        }
        return sb.toString().trim();
    }

    private String buildRiskUserPrompt(String contractText, String sanitizedContext) {
        String contextBlock = sanitizedContext.isBlank() ? "" : "\nContext: " + sanitizedContext + "\n";
        return """
                Review the contract text below and return five-category risk JSON.
                Cover: SUBJECT_INFO, PAYMENT, LIABILITY, TERM, DISPUTE_RESOLUTION.
                If a category has no clear risk, do not fabricate.
                %s
                Contract text:
                ---
                %s
                ---
                """.formatted(contextBlock, clip(contractText, 8000));
    }

    List<AiRiskVO> parseRiskItems(String content) {
        if (content == null || content.isBlank()) return List.of();
        try {
            String cleaned = cleanJson(content);
            JsonNode root = objectMapper.readTree(cleaned);
            if (root.isArray()) {
                return objectMapper.readValue(cleaned, new TypeReference<>() {});
            }
            if (root.isObject() && root.has("risks")) {
                return objectMapper.treeToValue(root.get("risks"), new TypeReference<>() {});
            }
            if (root.isObject() && root.has("items")) {
                return objectMapper.treeToValue(root.get("items"), new TypeReference<>() {});
            }
            return List.of();
        } catch (Exception ex) {
            throw new IllegalStateException("AI risk parse failed: " + ex.getMessage(), ex);
        }
    }

    private String chatCompletion(Map<String, Object> payload) throws IOException {
        String payloadJson = objectMapper.writeValueAsString(payload);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(qwenProperties.resolvedBaseUrl()))
                    .timeout(Duration.ofSeconds(qwenProperties.resolvedTimeoutSeconds()))
                    .header("Authorization", "Bearer " + qwenProperties.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 300) {
                throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode error = root.at("/error/message");
            if (!error.isMissingNode() && !error.asText().isBlank()) {
                throw new IOException(error.asText());
            }
            return root.at("/choices/0/message/content").asText("");
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException(ex.getMessage(), ex);
        }
    }

    private void assertQwenReady() {
        if (!qwenProperties.enabled()) {
            throw new IllegalStateException("Qwen is not enabled");
        }
        if (qwenProperties.apiKey() == null || qwenProperties.apiKey().isBlank()) {
            throw new IllegalStateException("Missing DASHSCOPE_API_KEY");
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
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "unknown" : current.getMessage();
    }

    private Map<String, Object> baseChatPayload(List<Map<String, String>> messages, boolean stream, boolean jsonObject) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", qwenProperties.resolvedModel());
        payload.put("messages", messages);
        if (stream) {
            payload.put("stream", true);
        }
        if (jsonObject) {
            payload.put("response_format", Map.of("type", "json_object"));
        }
        if (qwenProperties.resolvedEnableThinking()) {
            payload.put("enable_thinking", true);
        }
        int thinkingBudget = qwenProperties.resolvedThinkingBudget();
        if (thinkingBudget > 0) {
            payload.put("thinking_budget", thinkingBudget);
        }
        return payload;
    }

    private List<FulfillmentNode> parseFulfillmentNodes(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        try {
            String cleaned = cleanJson(content);
            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode nodes = root.isArray() ? root : root.path("nodes");
            if (!nodes.isArray()) {
                nodes = root.path("items");
            }
            if (!nodes.isArray()) {
                nodes = root.path("milestones");
            }
            if (!nodes.isArray()) {
                return List.of();
            }

            List<FulfillmentNode> result = new ArrayList<>();
            for (JsonNode node : nodes) {
                String nodeName = firstText(node, "nodeName", "name", "title", "milestoneName");
                if (nodeName.isBlank()) {
                    continue;
                }
                LocalDate plannedDate = parseDate(firstText(node, "plannedDate", "dueDate", "date", "planDate"));
                result.add(new FulfillmentNode(
                        nodeName,
                        firstText(node, "nodeType", "type", "planType"),
                        plannedDate,
                        firstText(node, "responsibleParty", "owner", "ownerName", "responsible"),
                        firstText(node, "sourceClause", "clause", "source", "sourceText"),
                        firstDouble(node, "confidence", "aiConfidence", "confidenceScore", "score"),
                        true,
                        plannedDate != null
                ));
                if (result.size() >= 12) {
                    break;
                }
            }
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("AI fulfillment parse failed: " + ex.getMessage(), ex);
        }
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && !value.asText("").isBlank()) {
                return value.asText("").trim();
            }
        }
        return "";
    }

    private Double firstDouble(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            Double parsed = parseDouble(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private Double parseDouble(JsonNode value) {
        double number;
        boolean percent = false;
        if (value.isNumber()) {
            number = value.asDouble();
        } else {
            String text = value.asText("").trim();
            if (text.isBlank()) {
                return null;
            }
            percent = text.endsWith("%");
            text = text.replace("%", "").trim();
            try {
                number = Double.parseDouble(text);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (Double.isNaN(number) || Double.isInfinite(number)) {
            return null;
        }
        if (percent || (number > 1D && number <= 100D)) {
            number = number / 100D;
        }
        return number;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
