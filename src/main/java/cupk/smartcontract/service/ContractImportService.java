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

/**
 * 合同文件导入编排服务。
 * 全权负责：OCR 触发、PaddleOCR 调用、Qwen 版式分析、HTML 生成、
 * 降级处理、ContractAttachmentOcr 记录管理、ContractImportResultVO 组装。
 */
@Service
public class ContractImportService {
    private static final Logger log = LoggerFactory.getLogger(ContractImportService.class);

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
                    false, null, null, null, null, List.of(), null);
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
        return "TECH";
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
