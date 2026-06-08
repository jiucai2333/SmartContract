package cupk.smartcontract.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cupk.smartcontract.config.QwenProperties;
import cupk.smartcontract.dto.AiDraftRequest;
import cupk.smartcontract.dto.AiDraftVO;
import cupk.smartcontract.dto.AiRiskReviewRequest;
import cupk.smartcontract.dto.AiRiskVO;
import cupk.smartcontract.dto.DraftTemplateVO.DraftField;
import cupk.smartcontract.mapper.ContractTemplateMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Service
public class AiDraftService {
    public static final String AI_NOTICE = "AI 辅助生成内容仅供参考，请由法务或业务负责人复核后使用";

    private static final String RISK_SYSTEM_PROMPT = """
            你是一名资深企业合同法务审查专家，负责对合同文本进行专业 AI 审核。
            请只基于用户提供的合同文本和脱敏业务背景进行分析，不要编造合同未出现的事实。

            审查维度：
            1. 合同条款是否符合法律法规和企业审批合规要求。
            2. 是否缺少验收、付款、违约责任、争议解决、保密、知识产权、解除、不可抗力等关键条款。
            3. 是否存在表述模糊、权利义务不对等、单方免责、付款条件不清、履约边界不清等风险。

            输出要求：
            - 仅返回 JSON，不要包含 Markdown 代码块或解释性文字。
            - JSON 格式为 {"risks":[{"level":"HIGH|MEDIUM|LOW","clause":"条款位置或标题；缺失条款写'缺失：条款名称'","reason":"风险原因","suggestion":"修改建议"}]}。
            - 如果没有发现明显风险，返回 {"risks":[]}。
            - 最多返回 10 条风险，优先返回高风险和影响审批的事项。
            """;

    private final QwenProperties qwenProperties;
    private final ObjectMapper objectMapper;
    private final ContractAttachmentService attachmentService;
    private final ContractTemplateMapper templateMapper;
    private final HttpClient httpClient;

    public AiDraftService(QwenProperties qwenProperties,
                          ObjectMapper objectMapper,
                          @Lazy ContractAttachmentService attachmentService,
                          ContractTemplateMapper templateMapper) {
        this.qwenProperties = qwenProperties;
        this.objectMapper = objectMapper;
        this.attachmentService = attachmentService;
        this.templateMapper = templateMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(qwenProperties.resolvedTimeoutSeconds()))
                .build();
    }

    public AiDraftVO generateDraft(AiDraftRequest request) {
        StringBuilder draft = new StringBuilder();
        streamFromQwen(request, draft::append);
        return new AiDraftVO(AI_NOTICE, draft.toString(), List.of(), sanitizedPrompt(request));
    }

    public SseEmitter streamDraft(AiDraftRequest request) {
        SseEmitter emitter = new SseEmitter(qwenProperties.resolvedSseTimeoutMs());
        AtomicBoolean finished = new AtomicBoolean(false);
        Thread worker = new Thread(() -> {
            try {
                assertQwenReady();
                send(emitter, finished, "notice", AI_NOTICE);
                send(emitter, finished, "prompt", sanitizedPrompt(request));
                send(emitter, finished, "source", "Qwen:" + qwenProperties.resolvedModel());
                streamFromQwen(request, token -> send(emitter, finished, "delta", token));
                complete(emitter, finished);
            } catch (Exception ex) {
                fail(emitter, finished, "调用 Qwen 生成合同草稿失败：" + rootMessage(ex));
            }
        }, "qwen-draft-stream");
        worker.setDaemon(true);
        worker.start();
        return emitter;
    }

    public List<AiRiskVO> analyzeRisks(AiRiskReviewRequest request) {
        assertQwenReady();
        String contractText = request.contractText();
        if (contractText == null || contractText.isBlank()) {
            return List.of();
        }
        String sanitizedContext = buildSanitizedContext(request);
        try {
            Map<String, Object> payload = baseChatPayload(
                    List.of(
                            Map.of("role", "system", "content", RISK_SYSTEM_PROMPT),
                            Map.of("role", "user", "content", buildRiskUserPrompt(contractText, sanitizedContext))
                    ),
                    false,
                    true
            );
            String content = chatCompletion(payload);
            return parseRiskItems(content);
        } catch (Exception ex) {
            throw new IllegalStateException("AI 风险审查失败：" + rootMessage(ex), ex);
        }
    }

    public String modelName() {
        return qwenProperties.resolvedModel();
    }

    public List<DraftField> analyzeDraftFields(String markdown) {
        assertQwenReady();
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        String prompt = """
                请从下面的合同模板 Markdown 中识别需要用户填写的字段。
                只返回 JSON，格式如下：
                {"fields":[{"key":"partyA","label":"甲方名称","value":"","placeholder":"请输入甲方名称","inputType":"text","required":true,"sourceHint":"字段来源说明"}]}
                key 可使用 partyA、partyB、duration、amount、deliverables、paymentNodes、signDate 等语义化名称。
                inputType 可使用 text、number、date、textarea。
                placeholder 应简洁说明用户需要填写什么；无法确定时可以为 null。

                模板内容：
                ---
                %s
                ---
                """.formatted(clipText(markdown, 12000));
        try {
            Map<String, Object> payload = baseChatPayload(
                    List.of(
                            Map.of("role", "system", "content", "你是合同模板字段识别助手，只输出结构化 JSON。"),
                            Map.of("role", "user", "content", prompt)
                    ),
                    false,
                    true
            );
            JsonNode root = objectMapper.readTree(chatCompletion(payload));
            JsonNode fields = root.path("fields");
            if (!fields.isArray()) {
                return List.of();
            }
            return objectMapper.treeToValue(fields, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("AI 模板字段识别失败：" + rootMessage(ex), ex);
        }
    }

    private String buildSanitizedContext(AiRiskReviewRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.contractType() != null && !request.contractType().isBlank()) {
            sb.append("合同类型：").append(request.contractType()).append("; ");
        }
        if (request.partyA() != null && !request.partyA().isBlank()) {
            sb.append("甲方：[COMPANY_A]; ");
        }
        if (request.partyB() != null && !request.partyB().isBlank()) {
            sb.append("乙方：[COMPANY_B]; ");
        }
        if (request.businessScope() != null && !request.businessScope().isBlank()) {
            sb.append("业务范围：").append(request.businessScope()).append("; ");
        }
        if (request.specialTerms() != null && !request.specialTerms().isBlank()) {
            sb.append("特殊条款：").append(request.specialTerms());
        }
        return sb.toString().trim();
    }

    private String buildRiskUserPrompt(String contractText, String sanitizedContext) {
        String contextBlock = sanitizedContext.isBlank() ? "" : "\n业务背景：" + sanitizedContext + "\n";
        return """
                请审查以下合同文本并返回风险 JSON。
                %s
                合同文本：
                ---
                %s
                ---
                """.formatted(contextBlock, clipText(contractText, 8000));
    }

    private List<AiRiskVO> parseRiskItems(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        try {
            String cleaned = content.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();

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
            throw new IllegalStateException("AI 风险审查结果解析失败：" + ex.getMessage(), ex);
        }
    }

    private String chatCompletion(Map<String, Object> payload) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(qwenProperties.resolvedBaseUrl()))
                    .timeout(Duration.ofSeconds(qwenProperties.resolvedTimeoutSeconds()))
                    .header("Authorization", "Bearer " + qwenProperties.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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

    private Map<String, Object> baseChatPayload(List<Map<String, String>> messages, boolean stream, boolean jsonObject) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("model", qwenProperties.resolvedModel());
        payload.put("messages", messages);
        if (stream) {
            payload.put("stream", true);
        }
        if (jsonObject) {
            payload.put("response_format", Map.of("type", "json_object"));
        }
        payload.put("enable_thinking", qwenProperties.resolvedEnableThinking());
        payload.put("thinking_budget", qwenProperties.resolvedThinkingBudget());
        return payload;
    }

    public String sanitizedPrompt(AiDraftRequest request) {
        return """
                合同类型：%s; 甲方：[COMPANY_A]; 乙方：[COMPANY_B]; 金额：[AMOUNT_X]; 业务范围：%s; 特殊条款：%s
                """.formatted(request.contractType(), request.businessScope(), Objects.toString(request.specialTerms(), "无")).trim();
    }

    private String resolveSystemPrompt(AiDraftRequest request) {
        return "你是企业合同起草助手。请根据用户输入生成结构清晰、条款完整、表达严谨的合同草稿，并提醒用户进行人工复核。";
    }

    private void streamFromQwen(AiDraftRequest request, Consumer<String> tokenConsumer) {
        try {
            assertQwenReady();
            Map<String, Object> payload = baseChatPayload(
                    List.of(
                            Map.of("role", "system", "content", resolveSystemPrompt(request)),
                            Map.of("role", "user", "content", buildPrompt(request))
                    ),
                    true,
                    false
            );
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(qwenProperties.resolvedBaseUrl()))
                    .timeout(Duration.ofSeconds(qwenProperties.resolvedTimeoutSeconds()))
                    .header("Authorization", "Bearer " + qwenProperties.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<java.io.InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 300) {
                String errorBody;
                try (var errorStream = response.body()) {
                    errorBody = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                }
                throw new IOException("HTTP " + response.statusCode() + ": " + errorBody);
            }
            try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    JsonNode root = objectMapper.readTree(data);
                    JsonNode error = root.at("/error/message");
                    if (!error.isMissingNode() && !error.asText().isBlank()) {
                        throw new IOException(error.asText());
                    }
                    JsonNode content = root.at("/choices/0/delta/content");
                    if (!content.isMissingNode() && !content.asText().isBlank()) {
                        tokenConsumer.accept(content.asText());
                    }
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private String buildPrompt(AiDraftRequest request) {
        String ocrRef = resolveOcrReference(request);
        String ocrBlock = ocrRef == null || ocrRef.isBlank()
                ? ""
                : "\n以下是 OCR 识别得到的参考文本，请结合使用但不要照搬错误识别内容：\n" + clipOcr(ocrRef, 6000) + "\n";
        return """
                请起草一份企业合同草稿，要求包含合同标题、双方信息、合同标的、履约安排、付款安排、知识产权、保密、违约责任、争议解决和签署条款。
                脱敏摘要：%s
                用户输入：甲方为 %s，乙方为 %s，金额为 %s。%s
                """.formatted(sanitizedPrompt(request), request.partyA(), request.partyB(), request.amount(), ocrBlock);
    }

    private String resolveOcrReference(AiDraftRequest request) {
        if (request.ocrReferenceText() != null && !request.ocrReferenceText().isBlank()) {
            return request.ocrReferenceText();
        }
        return attachmentService.resolveOcrReferenceText(request.attachmentId());
    }

    private String clipOcr(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private String clipText(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private void assertQwenReady() {
        if (!qwenProperties.enabled()) {
            throw new IllegalStateException("Qwen 服务未启用");
        }
        if (qwenProperties.apiKey() == null || qwenProperties.apiKey().isBlank()) {
            throw new IllegalStateException("缺少 DASHSCOPE_API_KEY，请配置环境变量或应用配置");
        }
    }

    private void send(SseEmitter emitter, AtomicBoolean finished, String eventName, Object data) {
        if (finished.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException ex) {
            finished.set(true);
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private void complete(SseEmitter emitter, AtomicBoolean finished) {
        if (finished.compareAndSet(false, true)) {
            emitter.complete();
        }
    }

    private void fail(SseEmitter emitter, AtomicBoolean finished, String message) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
        } catch (Exception ignored) {
        }
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }

    private String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "未知错误" : current.getMessage();
    }
}
