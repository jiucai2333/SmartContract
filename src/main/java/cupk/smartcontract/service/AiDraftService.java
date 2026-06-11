package cupk.smartcontract.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cupk.smartcontract.config.QwenProperties;
import cupk.smartcontract.entity.ContractTemplate;
import cupk.smartcontract.dto.AiDraftRequest;
import cupk.smartcontract.dto.AiDraftResponse;
import cupk.smartcontract.dto.AiRiskItem;
import cupk.smartcontract.dto.AiRiskReviewRequest;
import cupk.smartcontract.dto.DraftTemplateAnalysis.DraftField;
import cupk.smartcontract.mapper.ContractTemplateMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
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
    public static final String AI_NOTICE = "AI辅助生成，请人工审核确认";

    private static final String RISK_SYSTEM_PROMPT = """
            你是一名资深企业合同法务审查专家，精通中国合同法、公司法及相关行业法规。
            你的任务是对用户提交的合同文本进行专业风险审查，输出结构化的风险清单。

            审查维度（必须全部覆盖）：
            1. 条款合规自查：检查合同条款是否符合现行法律法规，是否存在违法或违规内容。
            2. 缺失条款检测：检查是否缺少关键条款，包括但不限于：验收标准与验收流程、争议解决方式、不可抗力条款、违约责任、保密条款、知识产权归属、合同解除条件。
            3. 不合理条款提示：检查是否存在单方面免责、权利义务严重不对等、模糊表述导致歧义、隐含不利条款等。

            输出要求：
            - 必须返回纯 JSON 数组，不要包含任何 Markdown 标记（如 ```json）或额外说明文字。
            - 每条风险包含四个字段：
              "level": 风险等级，只能取 HIGH、MEDIUM、LOW
              "clause": 问题条款的原文引用（缺失条款则写"缺失：XXX条款"）
              "reason": 风险原因的法律分析
              "suggestion": 具体的修改或补充建议
            - 如果合同文本无明显风险，返回空数组 []。
            - 最多返回 10 条风险项，按风险等级从高到低排列。
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

    public AiDraftResponse generateDraft(AiDraftRequest request) {
        StringBuilder draft = new StringBuilder();
        streamFromQwen(request, draft::append);
        return new AiDraftResponse(AI_NOTICE, draft.toString(), List.of(), sanitizedPrompt(request));
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
                fail(emitter, finished, "通义千问 Qwen 调用失败：" + rootMessage(ex));
            }
        }, "qwen-draft-stream");
        worker.setDaemon(true);
        worker.start();
        return emitter;
    }

    public List<AiRiskItem> analyzeRisks(AiRiskReviewRequest request) {
        assertQwenReady();
        String contractText = request.contractText();
        if (contractText == null || contractText.isBlank()) {
            return List.of();
        }
        String sanitizedContext = buildSanitizedContext(request);
        try {
            Map<String, Object> payload = Map.of(
                    "model", qwenProperties.resolvedModel(),
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", RISK_SYSTEM_PROMPT),
                            Map.of("role", "user", "content", buildRiskUserPrompt(contractText, sanitizedContext))
                    )
            );
            String content = chatCompletion(payload);
            return parseRiskItems(content);
        } catch (Exception ex) {
            throw new IllegalStateException("AI 风险审查失败：" + rootMessage(ex), ex);
        }
    }

    public List<DraftField> analyzeDraftFields(String markdown) {
        assertQwenReady();
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        String prompt = """
                请扫描以下合同模板，识别所有需要乙方人工填写或确认的空白项与关键字段。
                重点覆盖：甲方信息、乙方信息、工期或服务期限、合同金额、交付物、付款节点、签约日期。
                只返回 JSON 对象，格式为：
                {"fields":[{"key":"partyA","label":"甲方信息","value":"","placeholder":"模板中的原始空白占位符或null","inputType":"text","required":true,"sourceHint":"字段所在原文片段"}]}
                key 仅允许：partyA、partyB、duration、amount、deliverables、paymentNodes、signDate。
                inputType 仅允许：text、number、date、textarea。
                placeholder 必须是模板中可以直接替换的原始占位文本；没有明确占位符时返回 null。
                不要输出额外说明。

                合同模板：
                ---
                %s
                ---
                """.formatted(clipText(markdown, 12000));
        try {
            Map<String, Object> payload = Map.of(
                    "model", qwenProperties.resolvedModel(),
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", "你是企业合同模板字段抽取助手。"),
                            Map.of("role", "user", "content", prompt)
                    )
            );
            JsonNode root = objectMapper.readTree(chatCompletion(payload));
            JsonNode fields = root.path("fields");
            if (!fields.isArray()) {
                return List.of();
            }
            return objectMapper.treeToValue(fields, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("AI 待填字段扫描失败：" + rootMessage(ex), ex);
        }
    }

    private String buildSanitizedContext(AiRiskReviewRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.contractType() != null && !request.contractType().isBlank()) {
            sb.append("合同类型:").append(request.contractType()).append("; ");
        }
        if (request.partyA() != null && !request.partyA().isBlank()) {
            sb.append("甲方:[COMPANY_A]; ");
        }
        if (request.partyB() != null && !request.partyB().isBlank()) {
            sb.append("乙方:[COMPANY_B]; ");
        }
        if (request.businessScope() != null && !request.businessScope().isBlank()) {
            sb.append("业务:").append(request.businessScope()).append("; ");
        }
        if (request.specialTerms() != null && !request.specialTerms().isBlank()) {
            sb.append("特殊条款:").append(request.specialTerms());
        }
        return sb.toString().trim();
    }

    private String buildRiskUserPrompt(String contractText, String sanitizedContext) {
        String contextBlock = sanitizedContext.isBlank()
                ? ""
                : "\n脱敏上下文信息：" + sanitizedContext + "\n";
        return """
                请对以下合同文本进行风险审查，输出 JSON 数组。
                %s
                合同文本：
                ---
                %s
                ---
                
                请严格按照系统提示中的 JSON 格式输出风险清单，不要输出任何其他内容。
                """.formatted(contextBlock, clipText(contractText, 8000));
    }

    private List<AiRiskItem> parseRiskItems(String content) {
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

    public String sanitizedPrompt(AiDraftRequest request) {
        return """
                合同类型:%s; 甲方:[COMPANY_A]; 乙方:[COMPANY_B]; 金额:[AMOUNT_X]; 业务:%s; 特殊条款:%s
                """.formatted(request.contractType(), request.businessScope(), Objects.toString(request.specialTerms(), "无")).trim();
    }

    private String resolveSystemPrompt(AiDraftRequest request) {
        return "你是企业合同法务助理。输出中文合同初稿，结构清晰，必须包含AI辅助生成提示，并避免虚构法律结论。";
    }

    private void streamFromQwen(AiDraftRequest request, Consumer<String> tokenConsumer) {
        try {
            assertQwenReady();
            Map<String, Object> payload = Map.of(
                    "model", qwenProperties.resolvedModel(),
                    "stream", true,
                    "messages", List.of(
                            Map.of("role", "system", "content", resolveSystemPrompt(request)),
                            Map.of("role", "user", "content", buildPrompt(request))
                    )
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
                : "\n扫描件 OCR 参考（请优先对齐原文事实，勿编造未出现条款）：\n" + clipOcr(ocrRef, 6000) + "\n";
        return """
                请基于以下已脱敏信息生成合同初稿，输出需包含标题、主体信息、合作内容、金额与支付、验收、违约责任、保密、争议解决、AI辅助生成提示。
                脱敏输入：%s
                还原展示时甲方为：%s，乙方为：%s，金额为：%s 元。%s
                """.formatted(sanitizedPrompt(request), request.partyA(), request.partyB(), request.amount(), ocrBlock);
    }

    private String resolveOcrReference(AiDraftRequest request) {
        if (request.ocrReferenceText() != null && !request.ocrReferenceText().isBlank()) {
            return request.ocrReferenceText();
        }
        return attachmentService.resolveOcrReferenceText(request.attachmentId());
    }

    private String clipOcr(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private String clipText(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…（内容过长已截断）";
    }

    private void assertQwenReady() {
        if (!qwenProperties.enabled()) {
            throw new IllegalStateException("Qwen 未启用");
        }
        if (qwenProperties.apiKey() == null || qwenProperties.apiKey().isBlank()) {
            throw new IllegalStateException("缺少 DASHSCOPE_API_KEY 环境变量，请在系统环境或启动前设置");
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
