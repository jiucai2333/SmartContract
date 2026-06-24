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
import cupk.smartcontract.mapper.ContractAttachmentOcrMapper;
import cupk.smartcontract.mapper.ContractMainMapper;
import cupk.smartcontract.model.ContractDocumentModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final ContractDocumentImportService contractDocumentImportService;
    private final OcrEditorHtmlService ocrEditorHtmlService;
    private final ContractManagementService contractManagementService;
    private final ContractVersionService contractVersionService;
    private final ObjectMapper objectMapper;

    public ContractImportService(ContractAttachmentService attachmentService,
                                  ContractAttachmentOcrMapper attachmentOcrMapper,
                                  ContractMainMapper contractMainMapper,
                                  FileStorageService fileStorageService,
                                  ContractDocumentImportService contractDocumentImportService,
                                  OcrEditorHtmlService ocrEditorHtmlService,
                                  ContractManagementService contractManagementService,
                                  ContractVersionService contractVersionService,
                                  ObjectMapper objectMapper) {
        this.attachmentService = attachmentService;
        this.attachmentOcrMapper = attachmentOcrMapper;
        this.contractMainMapper = contractMainMapper;
        this.fileStorageService = fileStorageService;
        this.contractDocumentImportService = contractDocumentImportService;
        this.ocrEditorHtmlService = ocrEditorHtmlService;
        this.contractManagementService = contractManagementService;
        this.contractVersionService = contractVersionService;
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

    @Transactional(rollbackFor = Exception.class)
    public ContractMain createContractFromOcr(CreateContractFromOcrRequest request) throws Exception {
        ContractImportResultVO result = getImportResult(request.attachmentId());
        if (result == null || !"SUCCESS".equals(result.ocrStatus())) {
            throw new IllegalStateException("附件尚未完成 OCR 识别");
        }
        String editorHtml = result.editorHtml();
        if (!StringUtils.hasText(editorHtml)) {
            throw new IllegalStateException("OCR did not produce editable contract content");
        }
        String title = StringUtils.hasText(request.title()) ? request.title() : "OCR识别合同";
        String counterparty = StringUtils.hasText(request.counterparty()) ? request.counterparty() : "未知相对方";
        String type = mapContractType(StringUtils.hasText(request.type()) ? request.type() : "TECH");
        Long ownerId = SecurityContext.userId() != null ? SecurityContext.userId() : 1L;
        Long deptId = SecurityContext.deptId() != null ? SecurityContext.deptId() : 1L;
        ContractMain contract = contractManagementService.createContract(new ContractCreateRequest(
                title, type,
                BigDecimal.valueOf(10000),
                counterparty, deptId, ownerId, null, null,
                LocalDate.now().plusDays(90)
        ));
        attachmentService.link(request.attachmentId(), contract.getContractId());
        var version = contractVersionService.create(
                contract.getContractId(), editorHtml, null, "SAVE", SecurityContext.username());
        if (version.getFileId() == null) {
            throw new IllegalStateException("OCR contract version DOCX was not created");
        }
        return contract;
    }

    // ==================== VO 组装 ====================

    ContractImportResultVO buildImportResultVo(ContractAttachmentOcr ocr, String fileName) {
        if (ocr == null) {
            return new ContractImportResultVO(null, null, "PENDING", null, null,
                    false, false, false, null, null, null, null,
                    false, null, null, null, null, List.of(), null);
        }
        String editorHtml = buildEditorHtml(ocr);
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
                false,
                ocr.getOcrModel(),
                null,
                ocr.getOcrDurationMs(),
                null,
                ocr.getApproximate(),
                ocr.getPreviewHtml(),
                editorHtml,
                ocr.getPlainText(),
                preview,
                parseWarningsList(ocr.getParseWarnings()),
                ocr.getParseSource()
        );
    }

    /**
     * OCR imports expose one editable representation. Coordinate preview HTML is diagnostic only
     * and must never be used for editing, version content, or DOCX export.
     */
    private String buildEditorHtml(ContractAttachmentOcr ocr) {
        if (StringUtils.hasText(ocr.getEditorHtml())) {
            return ocr.getEditorHtml();
        }
        String editorHtml = ocrEditorHtmlService.buildEditableHtml(ocr.getOcrBlocksJson());
        if (!StringUtils.hasText(editorHtml)
                && StringUtils.hasText(ocr.getPreviewHtml())
                && isOfficeParserSource(ocr.getParseSource())) {
            editorHtml = ocr.getPreviewHtml();
        }
        return StringUtils.hasText(editorHtml)
                ? editorHtml : ocrEditorHtmlService.buildPlainTextHtml(ocr.getPlainText());
    }

    private boolean isOfficeParserSource(String source) {
        return "DOCX_POI".equalsIgnoreCase(source) || "DOC_HWPF".equalsIgnoreCase(source);
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
            ContractDocumentModel document =
                    contractDocumentImportService.importDocument(path, fileType, preserveFormat);
            ocr.setPlainText(document.plainText());
            ocr.setOcrRawJson(document.ocrRawJson());
            ocr.setOcrBlocksJson(document.ocrBlocksJson());
            ocr.setPreviewHtml(document.previewHtml());
            ocr.setParseSource(document.source());
            ocr.setApproximate(isApproximateParse(fileType, document.source()));
            ocr.setPageCount(document.pageCount());
            ocr.setOcrModel(document.ocrModel());
            ocr.setOcrDurationMs(document.ocrDurationMs());
            ocr.setEditorHtml(resolveRuleEditorHtml(document));
            warnings.addAll(document.warnings());
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

    // ==================== 工具方法 ====================

    private boolean isApproximateParse(String fileType, String source) {
        if (StringUtils.hasText(source) && source.toUpperCase(Locale.ROOT).startsWith("OCR")) {
            return true;
        }
        return List.of("jpg", "jpeg", "png", "webp").contains(fileType);
    }

    private String resolveRuleEditorHtml(ContractDocumentModel document) {
        if (document == null) return null;
        if (StringUtils.hasText(document.editorHtml())) return document.editorHtml();
        String editorHtml = ocrEditorHtmlService.buildEditableHtml(document.ocrBlocksJson());
        if (StringUtils.hasText(editorHtml)) return editorHtml;
        return ocrEditorHtmlService.buildPlainTextHtml(document.plainText());
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
