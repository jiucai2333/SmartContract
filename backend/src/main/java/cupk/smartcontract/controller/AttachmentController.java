package cupk.smartcontract.controller;

import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.dto.AttachmentVO;
import cupk.smartcontract.dto.LinkAttachmentRequest;
import cupk.smartcontract.service.ContractAttachmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 附件 Controller。
 * 仅负责附件的上传、查询、下载、关联合同。
 * OCR/导入接口已全部迁移至 {@link ContractImportController}。
 */
@RestController
@RequestMapping("/api")
public class AttachmentController {
    private final ContractAttachmentService attachmentService;

    public AttachmentController(ContractAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    // ==================== 附件 CRUD ====================

    /**
     * 上传附件（仅存储文件，不触发 OCR）。
     * 如需 OCR 识别，请先上传附件，再调用 POST /api/attachments/{id}/ocr。
     */
    @PostMapping("/attachments/upload")
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "FINANCE", "EXECUTIVE", "ADMIN"})
    public AttachmentVO uploadAttachment(@RequestParam("file") MultipartFile file,
                               @RequestParam(required = false) Long contractId,
                               @RequestParam(defaultValue = "CONTRACT_FILE") String attachType,
                               HttpServletRequest request) throws Exception {
        ContractAttachmentService.AttachmentCreatedResult created =
                attachmentService.createAttachment(file, contractId, attachType,
                        ContractAttachmentService.currentCreatedBy(request));
        return attachmentService.toAttachmentVo(created.attachment(), created.fileInfo());
    }

    @GetMapping("/attachments")
    public List<AttachmentVO> list(@RequestParam(required = false) Long contractId,
                                   @RequestParam(required = false) String ocrStatus) {
        return attachmentService.list(contractId, ocrStatus);
    }

    @GetMapping("/attachments/{attachmentId}")
    public AttachmentVO get(@PathVariable Long attachmentId) {
        return attachmentService.get(attachmentId);
    }

    @GetMapping("/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long attachmentId) {
        return attachmentService.download(attachmentId);
    }

    @PostMapping("/attachments/{attachmentId}/link")
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "FINANCE", "EXECUTIVE", "ADMIN"})
    public AttachmentVO linkAttachment(@PathVariable Long attachmentId, @Valid @RequestBody LinkAttachmentRequest request) {
        return attachmentService.link(attachmentId, request.contractId());
    }

    // ==================== 合同维度附件查询 ====================

    @GetMapping("/contracts/{contractId}/attachments")
    public List<AttachmentVO> contractAttachments(@PathVariable Long contractId) {
        return attachmentService.list(contractId, null);
    }

    @GetMapping("/contracts/{contractId}/attachment-count")
    public Map<String, Integer> attachmentCount(@PathVariable Long contractId) {
        return Map.of("count", attachmentService.countByContract(contractId));
    }
}
