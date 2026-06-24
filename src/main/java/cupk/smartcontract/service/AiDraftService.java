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
import cupk.smartcontract.dto.QwenLayoutVO;
import cupk.smartcontract.mapper.ContractTemplateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Service
public class AiDraftService {
    private static final Logger log = LoggerFactory.getLogger(AiDraftService.class);
    public static final String AI_NOTICE = "AI 辅助生成内容仅供参考，请由法务或业务负责人复核后使用";

    private static final String LAYOUT_SYSTEM_PROMPT = """
            你是合同文档版式结构分析助手。你只能根据输入的 OCR 结构化 JSON 判断文档结构，不能编造、改写、补充合同原文。
            你的任务不是起草合同，也不是风险审查，而是把 OCR 文本块分类为标题、条款标题、正文、表格、甲乙方信息、签字区、盖章区、页脚、页码或未知类型。
            你必须输出合法 JSON，不要输出 Markdown，不要输出解释性文字。对于无法判断的内容，block_type 必须使用 unknown。
            你不能猜测精确字号、页边距、行距、空格数量，只能输出 large、normal、small、unknown 等粗粒度字号等级。

            合同结构判断规则：
            1. 首页顶部较短的“采购合同、物资采购合同、设备采购合同、技术咨询合同、委托开发合同、软件技术服务合同、服务协议”等通常是 title。
            2. 含甲方、乙方、委托方、受托方、研究开发方、采购方、供货方、出租方、承租方、托运方、承运方、法定代表人、负责人、住所、地址、联系人、联系电话、统一社会信用代码等主体字段的内容优先为 party_info。
            3. 含“根据《中华人民共和国民法典》及有关法律法规、平等自愿协商一致、诚实信用、订立本合同、以资共同遵守”等引言是 paragraph，不是 heading。
            4. 以“第一条、第二条、第X条”开头的短文本通常是 heading。
            5. 以“一、二、三、四、五、六、七、八、九、十”开头且概括后续内容的短文本通常是 heading。
            6. 以“1.、1、1．、1.1、1.1.1、（1）”开头的内容通常是 paragraph，除非明显是短标题。
            7. 多列对齐且含名称、型号、数量、单价、金额、阶段、主要工作内容、阶段成果、完成时间、开户银行、账户名称、银行账号等字段的区域可判为 table，但优先服从 PaddleOCR 的 table 结果。
            8. 文档末尾含甲方（盖章）、乙方（签字）、法定代表人、委托代理人、授权代表、年 月 日、签订日期、盖章、签字等内容时判为 signature；明确独立的印章区域可判为 stamp。
            9. 页面底部单独数字通常是 page_number；重复出现在页面底部的说明性文字可判为 footer。
            10. 无法确定时使用 unknown。
            11. 不得根据合同类型新增条款，不得补出 OCR 中没有的名称、金额、日期或项目，不得改写原文。
            12. 只判断结构类型、阅读顺序、对齐倾向和粗粒度字号等级。
            """;

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
            - JSON 格式为 {"risks":[{"level":"HIGH|MEDIUM|LOW","category":"SUBJECT_INFO|PAYMENT|LIABILITY|TERM|DISPUTE_RESOLUTION","clause":"条款位置或标题","reason":"风险原因","suggestion":"修改建议"}]}。
            - 如果没有发现明显风险，返回 {"risks":[]}。
            - 最多返回 10 条风险，优先返回高风险和影响审批的事项。
            - 每一条风险必须落入上述五类之一，不要自创新分类。
            """;

    private final QwenProperties qwenProperties;
    private final ObjectMapper objectMapper;
    private final ContractImportService importService;
    private final ContractTemplateMapper templateMapper;
    private final HttpClient httpClient;

    public AiDraftService(QwenProperties qwenProperties,
                          ObjectMapper objectMapper,
                          @Lazy ContractImportService importService,
                          ContractTemplateMapper templateMapper) {
        this.qwenProperties = qwenProperties;
        this.objectMapper = objectMapper;
        this.importService = importService;
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
            Map<String, Object> payload = Map.of(
                    "model", qwenProperties.resolvedModel(),
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", "你是合同模板字段识别助手，只输出结构化 JSON。"),
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
            throw new IllegalStateException("AI 模板字段识别失败：" + rootMessage(ex), ex);
        }
    }

    public LayoutAnalysisResult analyzeOcrLayout(String ocrBlocksJson) {
        assertQwenReady();
        long startedAt = System.currentTimeMillis();
        log.info("[QwenLayout] Starting layout analysis");
        QwenOcrInputBuilder.BuildResult qwenInput =
                new QwenOcrInputBuilder(objectMapper).build(ocrBlocksJson);
        log.info("[QwenLayout] Input prepared: ocr_blocks_json length={}, qwen_input_json length={}, "
                        + "truncated={}, blocks={}/{}",
                qwenInput.originalLength(), qwenInput.compactLength(), qwenInput.truncated(),
                qwenInput.includedBlocks(), qwenInput.totalBlocks());
        qwenInput.warnings().forEach(warning -> log.warn("[QwenLayout] {}", warning));
        String prompt = """
                请根据以下 OCR 统一解析 JSON 中的 pages 和 blocks，对每个 block 进行版式结构判断。

                要求：
                1. 保留原 block_id。
                2. 不要改变原文含义，不要补充原文没有的信息。
                3. 不要根据合同类型新增条款。
                4. 如果无法判断类型，block_type 使用 unknown。
                5. order 表示推荐阅读顺序，从 1 开始。
                6. font_size_level 只能使用 large、normal、small、unknown。
                7. align 只能使用 left、center、right、unknown。
                8. block_type 只能使用 title、heading、paragraph、table、party_info、signature、stamp、footer、page_number、unknown。
                9. normalized_text 必须忠实保留 OCR 原文，不得改写。
                10. 输出必须是合法 JSON，不要输出 Markdown 包裹或解释文字。

                输出格式：
                {"blocks":[{"block_id":"p1_b1","block_type":"unknown","normalized_text":"","order":1,"align":"unknown","font_size_level":"unknown","reason":""}],"warnings":[]}

                OCR 统一解析 JSON：
                %s
                """.formatted(qwenInput.json());
        try {
            Map<String, Object> payload = Map.of(
                    "model", qwenProperties.resolvedLayoutModel(),
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", LAYOUT_SYSTEM_PROMPT),
                            Map.of("role", "user", "content", prompt)
                    )
            );
            String content = cleanJsonContent(chatCompletion(payload));
            QwenLayoutVO layout = objectMapper.readValue(content, QwenLayoutVO.class);
            layout = preserveOriginalOcrText(layout, ocrBlocksJson);
            validateLayout(layout);
            String json = objectMapper.writeValueAsString(layout);
            long duration = System.currentTimeMillis() - startedAt;
            log.info("[QwenLayout] Layout analysis completed in {} ms, blocks: {}",
                    duration, layout.blocks() == null ? 0 : layout.blocks().size());
            return new LayoutAnalysisResult(json, qwenProperties.resolvedLayoutModel(), duration);
        } catch (Exception ex) {
            log.warn("[QwenLayout] Layout analysis failed: {}", rootMessage(ex));
            throw new IllegalStateException("Qwen 版式结构分析失败：" + rootMessage(ex), ex);
        }
    }

    public record LayoutAnalysisResult(String json, String model, long durationMs) {
    }

    private String cleanJsonContent(String content) {
        String cleaned = content == null ? "" : content.trim();
        if (cleaned.startsWith("```json")) cleaned = cleaned.substring(7);
        else if (cleaned.startsWith("```")) cleaned = cleaned.substring(3);
        if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
        return cleaned.trim();
    }

    private void validateLayout(QwenLayoutVO layout) {
        if (layout == null || layout.blocks() == null) {
            throw new IllegalArgumentException("Qwen layout JSON does not contain blocks");
        }
        var blockTypes = java.util.Set.of("title", "heading", "paragraph", "table", "party_info",
                "signature", "stamp", "footer", "page_number", "unknown");
        var aligns = java.util.Set.of("left", "center", "right", "unknown");
        var sizes = java.util.Set.of("large", "normal", "small", "unknown");
        for (QwenLayoutVO.Block block : layout.blocks()) {
            if (block == null || block.blockId() == null || block.blockId().isBlank()) {
                throw new IllegalArgumentException("Qwen layout JSON contains an invalid block_id");
            }
            if (!blockTypes.contains(block.blockType())
                    || !aligns.contains(block.align())
                    || !sizes.contains(block.fontSizeLevel())) {
                throw new IllegalArgumentException("Qwen layout JSON contains an unsupported enum value");
            }
        }
    }

    private QwenLayoutVO preserveOriginalOcrText(QwenLayoutVO layout, String ocrBlocksJson) throws IOException {
        JsonNode root = objectMapper.readTree(ocrBlocksJson);
        Map<String, String> originalBlocks = new LinkedHashMap<>();
        JsonNode pages = root.path("pages");
        if (pages.isArray()) {
            for (JsonNode page : pages) {
                JsonNode blocks = page.path("blocks");
                if (!blocks.isArray()) continue;
                for (JsonNode block : blocks) {
                    String blockId = block.path("block_id").asText("");
                    if (!blockId.isBlank()) {
                        originalBlocks.put(blockId, block.path("text").asText(""));
                    }
                }
            }
        }
        Map<String, QwenLayoutVO.Block> analyzed = new LinkedHashMap<>();
        if (layout != null && layout.blocks() != null) {
            for (QwenLayoutVO.Block block : layout.blocks()) {
                if (block != null && originalBlocks.containsKey(block.blockId())) {
                    analyzed.put(block.blockId(), block);
                }
            }
        }

        List<QwenLayoutVO.Block> safeBlocks = new ArrayList<>();
        int fallbackOrder = 1;
        for (Map.Entry<String, String> original : originalBlocks.entrySet()) {
            QwenLayoutVO.Block block = analyzed.get(original.getKey());
            if (block == null) {
                safeBlocks.add(new QwenLayoutVO.Block(
                        original.getKey(), "unknown", original.getValue(), fallbackOrder++,
                        "unknown", "unknown", "Qwen did not return this OCR block."));
                continue;
            }
            safeBlocks.add(new QwenLayoutVO.Block(
                    block.blockId(),
                    block.blockType(),
                    original.getValue(),
                    block.order() > 0 ? block.order() : fallbackOrder,
                    block.align(),
                    block.fontSizeLevel(),
                    block.reason()
            ));
            fallbackOrder++;
        }
        List<String> warnings = layout == null || layout.warnings() == null
                ? new ArrayList<>() : new ArrayList<>(layout.warnings());
        if (analyzed.size() < originalBlocks.size()) {
            warnings.add("Some OCR blocks were missing from Qwen output and were retained as unknown.");
        }
        return new QwenLayoutVO(safeBlocks, warnings);
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
                请审查以下合同文本并返回五类风险 JSON。请特别覆盖主体信息、付款、违约、期限、争议解决五类风险；如果某类没有明显风险则不要强行编造。
                %s
                合同文本：
                ---
                %s
                ---
                """.formatted(contextBlock, clipText(contractText, 8000));
    }

    List<AiRiskVO> parseRiskItems(String content) {
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
                合同类型：%s; 甲方：[COMPANY_A]; 乙方：[COMPANY_B]; 金额：[AMOUNT_X]; 业务范围：%s; 特殊条款：%s
                """.formatted(request.contractType(), request.businessScope(), Objects.toString(request.specialTerms(), "无")).trim();
    }

    private String resolveSystemPrompt(AiDraftRequest request) {
        return "你是企业合同起草助手。请根据用户输入生成结构清晰、条款完整、表达严谨的合同草稿，并提醒用户进行人工复核。";
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
        return importService.resolveOcrReferenceText(request.attachmentId());
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
