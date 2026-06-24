package cupk.smartcontract.controller;

import cupk.smartcontract.entity.ContractMain;
import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.dto.AttachmentVO;
import cupk.smartcontract.dto.CreateContractFromOcrRequest;
import cupk.smartcontract.dto.LinkAttachmentRequest;
import cupk.smartcontract.service.ContractAttachmentService;
import cupk.smartcontract.service.DraftTemplateService;
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

@RestController
@RequestMapping("/api")
public class AttachmentController {
    private final ContractAttachmentService attachmentService;
    private final DraftTemplateService draftTemplateService;

    public AttachmentController(ContractAttachmentService attachmentService,
                                DraftTemplateService draftTemplateService) {
        this.attachmentService = attachmentService;
        this.draftTemplateService = draftTemplateService;
    }

    @PostMapping("/attachments/upload")
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    public AttachmentVO uploadAttachment(@RequestParam("file") MultipartFile file,
                               @RequestParam(required = false) Long contractId,
                               @RequestParam(defaultValue = "true") boolean runOcr,
                               @RequestParam(defaultValue = "CONTRACT_FILE") String attachType,
                               HttpServletRequest request) throws Exception {
        return attachmentService.upload(file, contractId, runOcr, attachType,
                ContractAttachmentService.currentCreatedBy(request));
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

    @PostMapping("/attachments/{attachmentId}/ocr")
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    public AttachmentVO ocrAttachment(@PathVariable Long attachmentId) throws Exception {
        return attachmentService.runOcr(attachmentId);
    }

    @GetMapping("/attachments/{attachmentId}/draft-analysis")
    public Object analyzeDraftTemplate(@PathVariable Long attachmentId) {
        return draftTemplateService.analyze(attachmentService.resolveOcrReferenceText(attachmentId));
    }

    @PostMapping("/attachments/{attachmentId}/draft-analysis")
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    public Object analyzeEditedDraftTemplate(@PathVariable Long attachmentId,
                                             @RequestBody Map<String, String> body) {
        String markdown = body.get("markdown");
        if (markdown == null || markdown.isBlank()) {
            markdown = attachmentService.resolveOcrReferenceText(attachmentId);
        }
        return draftTemplateService.analyze(markdown);
    }

    @PostMapping("/attachments/{attachmentId}/link")
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    public AttachmentVO linkAttachment(@PathVariable Long attachmentId, @Valid @RequestBody LinkAttachmentRequest request) {
        return attachmentService.link(attachmentId, request.contractId());
    }

    @PostMapping("/contracts/from-ocr")
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    public ContractMain createFromOcr(@Valid @RequestBody CreateContractFromOcrRequest request) throws Exception {
        return attachmentService.createContractFromOcr(request);
    }

    @GetMapping("/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long attachmentId) {
        return attachmentService.download(attachmentId);
    }

    @GetMapping("/contracts/{contractId}/attachments")
    public List<AttachmentVO> contractAttachments(@PathVariable Long contractId) {
        return attachmentService.list(contractId, null);
    }

    @GetMapping("/contracts/{contractId}/attachment-count")
    public Map<String, Integer> attachmentCount(@PathVariable Long contractId) {
        return Map.of("count", attachmentService.countByContract(contractId));
    }
}
