package cupk.smartcontract.web;

import cupk.smartcontract.common.RequireRole;
import cupk.smartcontract.common.SecurityContext;
import cupk.smartcontract.domain.ArchiveRecord;
import cupk.smartcontract.domain.ContractMain;
import cupk.smartcontract.domain.SealRecord;
import cupk.smartcontract.dto.ArchiveCreateRequest;
import cupk.smartcontract.dto.AiDraftRequest;
import cupk.smartcontract.dto.AiDraftResponse;
import cupk.smartcontract.dto.AiRiskItem;
import cupk.smartcontract.dto.AiRiskReviewRequest;
import cupk.smartcontract.dto.ContractCreateRequest;
import cupk.smartcontract.dto.DashboardSummary;
import cupk.smartcontract.dto.SealCreateRequest;
import cupk.smartcontract.service.AiDraftService;
import cupk.smartcontract.service.ContractManagementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ContractController {
    private final ContractManagementService service;
    private final AiDraftService aiDraftService;

    public ContractController(ContractManagementService service, AiDraftService aiDraftService) {
        this.service = service;
        this.aiDraftService = aiDraftService;
    }

    @GetMapping("/dashboard")
    public DashboardSummary dashboard() {
        return service.dashboard();
    }

    @GetMapping("/contracts")
    public Object contracts(@RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) String riskLevel) {
        return service.listContracts(keyword, status, riskLevel);
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    @PostMapping("/contracts")
    public ContractMain createContract(@Valid @RequestBody ContractCreateRequest request) {
        return service.createContract(request);
    }

    @PutMapping("/contracts/{contractId}")
    public ResponseEntity<?> updateContract(@PathVariable Long contractId,
                                           @Valid @RequestBody ContractCreateRequest request) {
        ContractMain existing = service.findContract(contractId);
        if ("ARCHIVED".equals(existing.getStatus())) {
            return ResponseEntity.status(403).body(Map.of("message", "合同已归档锁定，不可编辑"));
        }
        return ResponseEntity.ok(service.updateContract(contractId, request));
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    @PostMapping("/contracts/{contractId}/submit")
    public ResponseEntity<?> submit(@PathVariable Long contractId) {
        try {
            return ResponseEntity.ok(service.submitApproval(contractId));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @RequireRole({"DEPT_LEADER", "LEGAL", "EXECUTIVE", "ADMIN"})
    @GetMapping("/risks")
    public Object risks(@RequestParam(required = false) Long contractId) {
        return service.listRisks(contractId);
    }

    @RequireRole({"DEPT_LEADER", "LEGAL", "EXECUTIVE", "ADMIN"})
    @GetMapping("/approvals")
    public Object approvals() {
        return service.listApprovals();
    }

    @GetMapping("/fulfillment-plans")
    public Object plans() {
        return service.listPlans();
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    @PostMapping("/ai/draft")
    public AiDraftResponse draft(@Valid @RequestBody AiDraftRequest request) {
        return service.generateDraft(request);
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    @PostMapping("/ai/draft-stream")
    public SseEmitter draftStream(@Valid @RequestBody AiDraftRequest request) {
        return aiDraftService.streamDraft(request);
    }

    @RequireRole({"LEGAL", "EXECUTIVE", "ADMIN"})
    @PostMapping("/ai/risk-review")
    public ResponseEntity<?> riskReview(@Valid @RequestBody AiRiskReviewRequest request) {
        try {
            List<AiRiskItem> risks = aiDraftService.analyzeRisks(request);
            return ResponseEntity.ok(risks);
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    // ==================== 签章登记 & 归档确认 ====================

    @RequireRole({"LEGAL", "DEPT_LEADER", "ADMIN"})
    @PostMapping("/contracts/{contractId}/seal")
    public ResponseEntity<?> seal(@PathVariable Long contractId, @Valid @RequestBody SealCreateRequest request) {
        try { return ResponseEntity.ok(service.seal(request)); }
        catch (IllegalStateException | IllegalArgumentException ex) { return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage())); }
    }

    @RequireRole({"LEGAL", "DEPT_LEADER", "ADMIN"})
    @PostMapping("/contracts/{contractId}/archive")
    public ResponseEntity<?> archive(@PathVariable Long contractId, @Valid @RequestBody ArchiveCreateRequest request) {
        try { return ResponseEntity.ok(service.archive(request)); }
        catch (IllegalStateException | IllegalArgumentException ex) { return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage())); }
    }

    @GetMapping("/contracts/{contractId}/seal-records")
    public List<SealRecord> sealRecords(@PathVariable Long contractId) { return service.listSealRecords(contractId); }

    @GetMapping("/contracts/{contractId}/archive-records")
    public List<ArchiveRecord> archiveRecords(@PathVariable Long contractId) { return service.listArchiveRecords(contractId); }

    @GetMapping("/contracts/{contractId}/versions/{versionId}/is-locked")
    public Map<String, Boolean> isVersionLocked(@PathVariable Long contractId, @PathVariable Long versionId) {
        return Map.of("locked", service.isVersionLocked(versionId));
    }

    @RequireRole({"ADMIN"})
    @PutMapping("/admin/contracts/{contractId}/fields")
    public ResponseEntity<?> adminUpdateFields(@PathVariable Long contractId, @RequestBody Map<String, String> body) {
        try {
            ContractMain contract = service.findContract(contractId);
            if (body.containsKey("status") && body.get("status") != null) {
                String target = body.get("status").trim();
                if (!target.equals(contract.getStatus())) service.getStatusTransitionService().transition(contract, target);
            }
            if (body.containsKey("riskLevel") && body.get("riskLevel") != null) {
                contract.setRiskLevel(body.get("riskLevel").trim());
                contract.setUpdatedAt(LocalDateTime.now());
                contract.setUpdatedBy("ADMIN_" + SecurityContext.userId());
                service.getContractMapper().updateById(contract);
            }
            service.getStatusTransitionService().writeLog(SecurityContext.userId(), "ADMIN_EDIT", "CONTRACT", contractId, "SUCCESS");
            return ResponseEntity.ok(Map.of("message", "更新成功"));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}
