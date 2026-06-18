package cupk.smartcontract.controller;

import cupk.smartcontract.entity.ContractMain;
import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.dto.ContractImportResultVO;
import cupk.smartcontract.dto.CreateContractFromOcrRequest;
import cupk.smartcontract.service.ContractAttachmentService;
import cupk.smartcontract.service.ContractImportService;
import cupk.smartcontract.service.DraftTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 合同导入 Controller。
 * 负责 PDF/图片上传 → OCR → Qwen 版式分析 → HTML 生成的全流程。
 */
@RestController
@RequestMapping("/api")
public class ContractImportController {
    private final ContractAttachmentService attachmentService;
    private final ContractImportService importService;
    private final DraftTemplateService draftTemplateService;

    public ContractImportController(ContractAttachmentService attachmentService,
                                     ContractImportService importService,
                                     DraftTemplateService draftTemplateService) {
        this.attachmentService = attachmentService;
        this.importService = importService;
        this.draftTemplateService = draftTemplateService;
    }

    // ==================== OCR 触发与查询 ====================

    /**
     * 对已有附件触发 OCR 识别。
     */
    @PostMapping("/attachments/{attachmentId}/ocr")
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    public ContractImportResultVO runOcr(@PathVariable Long attachmentId,
                                          @RequestParam(defaultValue = "false") boolean preserveFormat) throws Exception {
        return importService.runOcr(attachmentId, preserveFormat);
    }

    /**
     * 查询 OCR/导入结果。
     */
    @GetMapping("/attachments/{attachmentId}/ocr")
    public ContractImportResultVO getOcrResult(@PathVariable Long attachmentId) {
        return importService.getImportResult(attachmentId);
    }

    // ==================== 上传 + 导入（一体化） ====================

    /**
     * 上传合同文件并触发 OCR 导入编排。
     */
    @PostMapping("/contracts/import/upload")
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    public ContractImportResultVO uploadAndImport(@RequestParam("file") MultipartFile file,
                                                   @RequestParam(required = false) Long contractId,
                                                   @RequestParam(defaultValue = "true") boolean runOcr,
                                                   @RequestParam(defaultValue = "false") boolean preserveFormat,
                                                   @RequestParam(defaultValue = "CONTRACT_FILE") String attachType,
                                                   HttpServletRequest request) throws Exception {
        ContractAttachmentService.AttachmentCreatedResult created =
                attachmentService.createAttachment(file, contractId, attachType,
                        ContractAttachmentService.currentCreatedBy(request));
        return importService.uploadAndImport(created, runOcr, preserveFormat);
    }

    // ==================== 从 OCR 创建合同 ====================

    @PostMapping("/contracts/from-ocr")
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    public ContractMain createFromOcr(@Valid @RequestBody CreateContractFromOcrRequest request) throws Exception {
        return importService.createContractFromOcr(request);
    }

    // ==================== 草稿模板分析（基于附件 OCR 文本） ====================

    @GetMapping("/attachments/{attachmentId}/draft-analysis")
    public Object analyzeDraftTemplate(@PathVariable Long attachmentId) {
        return draftTemplateService.analyze(importService.resolveOcrReferenceText(attachmentId));
    }

    @PostMapping("/attachments/{attachmentId}/draft-analysis")
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    public Object analyzeEditedDraftTemplate(@PathVariable Long attachmentId,
                                             @RequestBody Map<String, String> body) {
        String markdown = body.get("markdown");
        if (markdown == null || markdown.isBlank()) {
            markdown = importService.resolveOcrReferenceText(attachmentId);
        }
        return draftTemplateService.analyze(markdown);
    }
}
