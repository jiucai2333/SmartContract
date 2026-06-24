package cupk.smartcontract.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cupk.smartcontract.security.SecurityContext;
import cupk.smartcontract.entity.ContractAttachment;
import cupk.smartcontract.entity.ContractAttachmentOcr;
import cupk.smartcontract.entity.ContractMain;
import cupk.smartcontract.entity.FileInfo;
import cupk.smartcontract.dto.ContractCreateRequest;
import cupk.smartcontract.dto.ContractImportResultVO;
import cupk.smartcontract.dto.CreateContractFromOcrRequest;
import cupk.smartcontract.dto.OcrExtractVO;
import cupk.smartcontract.mapper.ContractAttachmentOcrMapper;
import cupk.smartcontract.mapper.ContractMainMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 合同文件导入编排服务。
 * 全权负责：OCR 触发、PaddleOCR 调用、Qwen 版式分析、HTML 生成、
 * 降级处理、ContractAttachmentOcr 记录管理、ContractImportResultVO 组装。
 */
@Service
public class ContractImportService {
    private static final Logger log = LoggerFactory.getLogger(ContractImportService.class);
    private static final String META_LABELS =
            "合同名称|合同标题|项目名称|合同相对方|相对方|乙方|甲方|采购方|供货方|委托方|受托方|买方|卖方|合同类型|合同类别|合同金额|总金额|价款|金额";
    private static final Pattern LABEL_VALUE_PATTERN = Pattern.compile(
            "(?s)(" + META_LABELS + ")\\s*[:：]?\\s*(.+?)(?=\\s*(?:" + META_LABELS + ")\\s*[:：]?|[\\n\\r；;]|$)");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?:合同金额|总金额|价款|金额|人民币|合计|总价)[^\\d]{0,12}([￥¥]?\\s*[0-9][0-9,]*(?:\\.[0-9]+)?\\s*(?:元|万元|万|亿元|亿)?)");
    private static final Pattern FIRST_MONEY_PATTERN = Pattern.compile(
            "([￥¥]\\s*[0-9][0-9,]*(?:\\.[0-9]+)?|[0-9][0-9,]*(?:\\.[0-9]+)?\\s*(?:元|万元|万|亿元|亿))");
    private static final List<String> CONTRACT_TYPES = List.of(
            "采购", "销售", "技术", "劳务", "劳动", "保密", "物流", "运输", "企业服务", "服务", "知识产权", "租赁");

    private final ContractAttachmentService attachmentService;
    private final ContractAttachmentOcrMapper attachmentOcrMapper;
    private final ContractMainMapper contractMainMapper;
    private final FileStorageService fileStorageService;
    private final OcrService ocrService;
    private final AiDraftService aiDraftService;
    private final OcrLayoutHtmlService ocrLayoutHtmlService;
    private final DocumentParseService documentParseService;
    private final ContractManagementService contractManagementService;
    private final ObjectMapper objectMapper;

    public ContractImportService(ContractAttachmentService attachmentService,
                                  ContractAttachmentOcrMapper attachmentOcrMapper,
                                  ContractMainMapper contractMainMapper,
                                  FileStorageService fileStorageService,
                                  OcrService ocrService,
                                  AiDraftService aiDraftService,
                                  OcrLayoutHtmlService ocrLayoutHtmlService,
                                  DocumentParseService documentParseService,
                                  ContractManagementService contractManagementService,
                                  ObjectMapper objectMapper) {
        this.attachmentService = attachmentService;
        this.attachmentOcrMapper = attachmentOcrMapper;
        this.contractMainMapper = contractMainMapper;
        this.fileStorageService = fileStorageService;
        this.ocrService = ocrService;
        this.aiDraftService = aiDraftService;
        this.ocrLayoutHtmlService = ocrLayoutHtmlService;
        this.documentParseService = documentParseService;
        this.contractManagementService = contractManagementService;
        this.objectMapper = objectMapper;
    }

    // ==================== 导入入口 ====================

    /**
     * 上传文件并触发 OCR 导入编排。
     */
    public ContractImportResultVO uploadAndImport(ContractAttachmentService.AttachmentCreatedResult created,
                                                   boolean runOcr, boolean preserveFormat) throws Exception {
        ContractAttachment attachment = created.attachment();
        FileInfo fileInfo = created.fileInfo();
        boolean shouldRunOcr = created.signedFile() ? false : runOcr;

        ContractAttachmentOcr ocr = newOcr(attachment, shouldRunOcr ? "PROCESSING" : "PENDING");
        attachmentOcrMapper.insert(ocr);

        if (shouldRunOcr) {
            runOcrPipeline(attachment, ocr, fileInfo, preserveFormat);
        }

        return buildImportResultVo(ocr, fileInfo.getFileName());
    }

    /**
     * 对已有附件触发 OCR。
     */
    public ContractImportResultVO runOcr(Long attachmentId, boolean preserveFormat) throws Exception {
        ContractAttachment attachment = attachmentService.requireAccessibleAttachment(attachmentId);
        FileInfo fileInfo = attachmentService.requireFileInfo(attachment.getFileId());
        ContractAttachmentOcr ocr = attachmentOcrMapper.selectByAttachmentId(attachmentId);
        if (ocr == null) {
            ocr = newOcr(attachment, "PROCESSING");
            attachmentOcrMapper.insert(ocr);
        } else {
            ocr.setOcrStatus("PROCESSING");
            ocr.setOcrError(null);
            ocr.setUpdatedBy(currentCreatedBy());
            ocr.setUpdatedAt(LocalDateTime.now());
            attachmentOcrMapper.upsertByAttachmentId(ocr);
        }
        runOcrPipeline(attachment, ocr, fileInfo, preserveFormat);
        return buildImportResultVo(ocr, fileInfo.getFileName());
    }

    // ==================== 导入结果查询 ====================

    public ContractImportResultVO getImportResult(Long attachmentId) {
        ContractAttachment attachment = attachmentService.requireAccessibleAttachment(attachmentId);
        FileInfo fileInfo = attachmentService.requireFileInfo(attachment.getFileId());
        ContractAttachmentOcr ocr = attachmentOcrMapper.selectByAttachmentId(attachmentId);
        return buildImportResultVo(ocr, fileInfo.getFileName());
    }

    public String resolveOcrReferenceText(Long attachmentId) {
        if (attachmentId == null) return null;
        try {
            ContractAttachmentOcr ocr = attachmentOcrMapper.selectByAttachmentId(attachmentId);
            return ocr != null ? ocr.getPlainText() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    // ==================== 从 OCR 创建合同 ====================

    public ContractMain createContractFromOcr(CreateContractFromOcrRequest request) throws Exception {
        ContractImportResultVO result = getImportResult(request.attachmentId());
        if (result == null || !"SUCCESS".equals(result.ocrStatus())) {
            throw new IllegalStateException("附件尚未完成 OCR 识别");
        }
        String plainText = result.plainText();
        String title = StringUtils.hasText(request.title()) ? request.title() : "OCR识别合同";
        String counterparty = StringUtils.hasText(request.counterparty()) ? request.counterparty() : "未知相对方";
        String type = mapContractType(StringUtils.hasText(request.type()) ? request.type() : "TECH");
        Long ownerId = SecurityContext.userId() != null ? SecurityContext.userId() : 1L;
        Long deptId = SecurityContext.deptId() != null ? SecurityContext.deptId() : 1L;
        ContractMain contract = contractManagementService.createContract(new ContractCreateRequest(
                title, type,
                BigDecimal.valueOf(10000),
                counterparty, deptId, ownerId, null,
                LocalDate.now().plusDays(90)
        ));
        attachmentService.link(request.attachmentId(), contract.getContractId());
        return contract;
    }

    // ==================== VO 组装 ====================

    ContractImportResultVO buildImportResultVo(ContractAttachmentOcr ocr, String fileName) {
        if (ocr == null) {
            return new ContractImportResultVO(null, null, "PENDING", null, null,
                    false, false, false, null, null, null, null,
                    false, null, null, null, null, null, List.of(), null);
        }
        String editorHtml = ocrLayoutHtmlService.buildEditableHtml(
                ocr.getOcrBlocksJson(), ocr.getQwenLayoutJson());
        if (!StringUtils.hasText(editorHtml)
                && !StringUtils.hasText(ocr.getOcrBlocksJson())
                && StringUtils.hasText(ocr.getPreviewHtml())) {
            editorHtml = ocr.getPreviewHtml();
        }
        if (!StringUtils.hasText(editorHtml)) {
            editorHtml = ocrLayoutHtmlService.buildPlainTextHtml(ocr.getPlainText());
        }
        String preview = ocr.getPlainText();
        if (preview != null && preview.length() > 200) {
            preview = preview.substring(0, 200) + "...";
        }
        OcrExtractVO extract = extractContractMeta(ocr.getPlainText(), fileName);
        return new ContractImportResultVO(
                ocr.getAttachmentId(),
                fileName,
                ocr.getOcrStatus(),
                ocr.getOcrError(),
                ocr.getPageCount(),
                ocr.getOcrRawJson() != null && !ocr.getOcrRawJson().isBlank(),
                ocr.getOcrBlocksJson() != null && !ocr.getOcrBlocksJson().isBlank(),
                ocr.getQwenLayoutJson() != null && !ocr.getQwenLayoutJson().isBlank(),
                ocr.getOcrModel(),
                ocr.getQwenModel(),
                ocr.getOcrDurationMs(),
                ocr.getQwenDurationMs(),
                ocr.getApproximate(),
                ocr.getPreviewHtml(),
                editorHtml,
                ocr.getPlainText(),
                preview,
                extract,
                parseWarningsList(ocr.getParseWarnings()),
                ocr.getParseSource()
        );
    }

    // ==================== OCR 编排 ====================

    private void runOcrPipeline(ContractAttachment attachment, ContractAttachmentOcr ocr,
                                 FileInfo fileInfo, boolean preserveFormat) throws Exception {
        List<String> warnings = new ArrayList<>();
        log.info("[ContractOCR] Starting attachment {}, file type {}",
                attachment.getAttachmentId(), fileInfo.getFileType());
        try {
            Path path = fileStorageService.resolve(fileInfo.getObjectKey());
            String fileType = fileInfo.getFileType() == null ? "" : fileInfo.getFileType().toLowerCase(Locale.ROOT);
            if (isPaddleContractType(fileType)) {
                OcrService.OcrProcessResult result = ocrService.process(path, fileType);
                ocr.setPlainText(result.rawText());
                ocr.setOcrRawJson(result.rawJson());
                ocr.setOcrBlocksJson(result.parseJson());
                ocr.setApproximate(true);
                ocr.setOcrModel(result.model());
                ocr.setOcrDurationMs(result.durationMs());
                ocr.setPageCount(result.pageCount());
                warnings.addAll(result.warnings());
                runQwenLayout(attachment, ocr, result, warnings);
                applyPreviewHtml(ocr, result, warnings);
            } else {
                DocumentParseService.ParseResult result =
                        documentParseService.parse(path, fileType, preserveFormat);
                ocr.setPreviewHtml(result.html());
                ocr.setPlainText(org.jsoup.Jsoup.parse(result.html()).text());
                ocr.setParseSource(result.parser());
                ocr.setApproximate(false);
                ocr.setPageCount(result.pageCount());
            }
            ocr.setOcrStatus("SUCCESS");
            ocr.setOcrError(null);
            log.info("[ContractOCR] Completed attachment {}, pages {}, source {}",
                    attachment.getAttachmentId(), ocr.getPageCount(), ocr.getParseSource());
        } catch (Exception ex) {
            ocr.setOcrStatus("FAILED");
            ocr.setOcrError(trimError(ex.getMessage()));
            warnings.add("OCR failed: " + trimError(ex.getMessage()));
            log.warn("[ContractOCR] Failed attachment {}: {}",
                    attachment.getAttachmentId(), trimError(ex.getMessage()));
        }
        ocr.setParseWarnings(writeWarnings(warnings));
        ocr.setUpdatedBy(currentCreatedBy());
        ocr.setUpdatedAt(LocalDateTime.now());
        attachmentOcrMapper.upsertByAttachmentId(ocr);
        if ("FAILED".equals(ocr.getOcrStatus())) {
            throw new IllegalStateException(ocr.getOcrError());
        }
    }

    private void runQwenLayout(ContractAttachment attachment, ContractAttachmentOcr ocr,
                               OcrService.OcrProcessResult result, List<String> warnings) {
        if (!StringUtils.hasText(result.parseJson())) {
            warnings.add("Qwen layout analysis was skipped because OCR blocks are unavailable.");
            return;
        }
        try {
            AiDraftService.LayoutAnalysisResult layout =
                    aiDraftService.analyzeOcrLayout(result.parseJson());
            ocr.setQwenLayoutJson(layout.json());
            ocr.setQwenModel(layout.model());
            ocr.setQwenDurationMs(layout.durationMs());
        } catch (Exception ex) {
            warnings.add("Qwen layout analysis failed; OCR coordinate layout will be used: "
                    + trimError(ex.getMessage()));
            log.warn("[ContractOCR] Qwen layout fallback for attachment {}: {}",
                    attachment.getAttachmentId(), trimError(ex.getMessage()));
        }
    }

    private void applyPreviewHtml(ContractAttachmentOcr ocr, OcrService.OcrProcessResult result,
                                  List<String> warnings) {
        String editorHtml = ocrLayoutHtmlService.buildEditableHtml(
                result.parseJson(), ocr.getQwenLayoutJson());
        if (!StringUtils.hasText(editorHtml)
                && StringUtils.hasText(ocrLayoutHtmlService.buildPlainTextHtml(result.rawText()))) {
            warnings.add("Structured editor HTML was unavailable; plain OCR text will be used.");
        }
        OcrLayoutHtmlService.BuildResult built =
                ocrLayoutHtmlService.build(result.parseJson(), ocr.getQwenLayoutJson());
        warnings.addAll(built.warnings());
        if (StringUtils.hasText(built.html())) {
            ocr.setPreviewHtml(built.html());
            ocr.setParseSource(built.source());
            return;
        }
        String plainTextHtml = ocrLayoutHtmlService.buildPlainTextHtml(result.rawText());
        if (StringUtils.hasText(plainTextHtml)) {
            ocr.setPreviewHtml(plainTextHtml);
            ocr.setParseSource("plain_text");
            warnings.add("Structured OCR blocks were unavailable; plain OCR text was used.");
            return;
        }
        if (StringUtils.hasText(result.legacyMarkdownHtml())) {
            ocr.setPreviewHtml(result.legacyMarkdownHtml());
            ocr.setParseSource("legacy_markdown");
            warnings.add("All structured OCR fallbacks failed; legacy Paddle markdown was used.");
            return;
        }
        throw new IllegalStateException("OCR 未生成可用于编辑器的文本内容");
    }

    // ==================== 工具方法 ====================

    private boolean isPaddleContractType(String fileType) {
        return List.of("jpg", "jpeg", "png", "webp").contains(fileType);
    }

    private ContractAttachmentOcr newOcr(ContractAttachment attachment, String status) {
        LocalDateTime now = LocalDateTime.now();
        ContractAttachmentOcr ocr = new ContractAttachmentOcr();
        ocr.setAttachmentId(attachment.getAttachmentId());
        ocr.setOcrStatus(status);
        ocr.setApproximate(false);
        ocr.setCreatedBy(attachment.getCreatedBy());
        ocr.setCreatedAt(now);
        ocr.setUpdatedBy(attachment.getUpdatedBy());
        ocr.setUpdatedAt(now);
        ocr.setDeleted(0);
        ocr.setVersion(1);
        return ocr;
    }

    private String writeWarnings(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(warnings);
        } catch (Exception ex) {
            return String.join("\n", warnings);
        }
    }

    private List<String> parseWarningsList(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ex) {
            return List.of(json);
        }
    }

    private String mapContractType(String contractType) {
        if (!StringUtils.hasText(contractType)) return "TECH";
        if (contractType.contains("采购")) return "PURCHASE";
        if (contractType.contains("销售")) return "SALES";
        if (contractType.contains("劳务")) return "LABOR";
        if (contractType.contains("技术")) return "TECH";
        if (contractType.contains("劳动")) return "LABOR";
        if (contractType.contains("保密")) return "CONFIDENTIAL";
        if (contractType.contains("物流") || contractType.contains("运输")) return "LOGISTICS";
        if (contractType.contains("企业服务") || contractType.contains("服务")) return "ENTERPRISE_SERVICE";
        if (contractType.contains("知识产权")) return "INTELLECTUAL_PROPERTY";
        return "TECH";
    }

    private OcrExtractVO extractContractMeta(String rawText, String fileName) {
        String text = normalizeText(rawText);
        if (!StringUtils.hasText(text)) {
            return new OcrExtractVO(null, null, null, titleFromFileName(fileName), null,
                    null, null, null, rawText);
        }
        String title = firstText(labelValue(text, "合同名称", "合同标题", "项目名称"),
                detectTitle(text), titleFromFileName(fileName));
        String partyA = firstText(labelValue(text, "甲方", "采购方", "委托方", "买方"),
                detectParty(text, "甲方", "采购方", "委托方", "买方"));
        String partyB = firstText(labelValue(text, "合同相对方", "相对方", "乙方", "供货方", "受托方", "卖方"),
                detectParty(text, "乙方", "供货方", "受托方", "卖方"));
        String counterparty = firstText(labelValue(text, "合同相对方", "相对方"), partyB, partyA);
        String typeLabel = firstText(labelValue(text, "合同类型", "合同类别"), detectContractTypeText(text), title);
        BigDecimal amount = firstAmount(labelValue(text, "合同金额", "总金额", "价款", "金额"), text);
        return new OcrExtractVO(mapContractType(typeLabel), partyA, partyB, title, counterparty,
                amount, null, null, rawText);
    }

    private String normalizeText(String text) {
        return Objects.toString(text, "")
                .replace('\u00A0', ' ')
                .replace("\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String labelValue(String text, String... labels) {
        Matcher matcher = LABEL_VALUE_PATTERN.matcher(text);
        while (matcher.find()) {
            String label = matcher.group(1);
            for (String expected : labels) {
                if (!expected.equals(label)) continue;
                String value = cleanExtractedText(matcher.group(2));
                if (StringUtils.hasText(value)) return value;
            }
        }
        return null;
    }

    private String detectTitle(String text) {
        for (String rawLine : text.split("\\n")) {
            String line = cleanExtractedText(rawLine);
            if (!StringUtils.hasText(line)) continue;
            if (line.length() <= 60 && line.contains("合同")
                    && !line.matches(".*[，。；:：].*")
                    && !line.contains("合同名称")
                    && !line.contains("合同相对方")) {
                return line;
            }
        }
        return null;
    }

    private String detectParty(String text, String... labels) {
        for (String label : labels) {
            Pattern pattern = Pattern.compile(label + "\\s*(?:\\([^)]*\\))?\\s*[:：]\\s*([^\\n\\r；;]+)");
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String value = cleanExtractedText(matcher.group(1));
                if (StringUtils.hasText(value)) return value;
            }
        }
        return null;
    }

    private String detectContractTypeText(String text) {
        for (String type : CONTRACT_TYPES) {
            if (text.contains(type + "合同")) return type;
        }
        return null;
    }

    private BigDecimal firstAmount(String labelledValue, String text) {
        BigDecimal labelledAmount = parseAmount(labelledValue);
        if (labelledAmount != null) return labelledAmount;
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        if (matcher.find()) {
            BigDecimal amount = parseAmount(matcher.group(1));
            if (amount != null) return amount;
        }
        matcher = FIRST_MONEY_PATTERN.matcher(text);
        if (matcher.find()) return parseAmount(matcher.group(1));
        return null;
    }

    private BigDecimal parseAmount(String value) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.replace(",", "").replace("￥", "").replace("¥", "").trim();
        Matcher number = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)").matcher(normalized);
        if (!number.find()) return null;
        try {
            BigDecimal amount = new BigDecimal(number.group(1));
            if (normalized.contains("亿")) return amount.multiply(new BigDecimal("100000000"));
            if (normalized.contains("万")) return amount.multiply(new BigDecimal("10000"));
            return amount;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String titleFromFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) return null;
        String title = fileName.replaceFirst("\\.[^.]+$", "");
        title = title.replaceFirst("^[0-9A-Za-z_-]+[-_]", "");
        return cleanExtractedText(title);
    }

    private String firstText(String... values) {
        for (String value : values) {
            String cleaned = cleanExtractedText(value);
            if (StringUtils.hasText(cleaned)) return cleaned;
        }
        return null;
    }

    private String cleanExtractedText(String value) {
        if (!StringUtils.hasText(value)) return null;
        return value.replaceAll("[：:]+$", "")
                .replaceAll("\\s+", " ")
                .replaceAll("^(名称|为|是)[:：]?", "")
                .trim();
    }

    private String trimError(String message) {
        if (message == null) return "OCR 识别失败";
        return message.length() > 480 ? message.substring(0, 480) : message;
    }

    private String currentCreatedBy() {
        Long userId = SecurityContext.userId();
        return userId == null ? SecurityContext.username() : String.valueOf(userId);
    }
}
