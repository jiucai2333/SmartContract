package cupk.smartcontract.controller;

import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.entity.ContractMain;
import cupk.smartcontract.dto.AiDraftRequest;
import cupk.smartcontract.dto.AiDraftVO;
import cupk.smartcontract.dto.ContractCreateRequest;
import cupk.smartcontract.service.AiDraftService;
import cupk.smartcontract.service.ContractManagementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ContractController {
    private final ContractManagementService contractService;
    private final AiDraftService aiDraftService;

    public ContractController(ContractManagementService contractService, AiDraftService aiDraftService) {
        this.contractService = contractService;
        this.aiDraftService = aiDraftService;
    }

    // ==================== Contracts ====================

    @GetMapping("/contracts")
    public Object listContracts(@RequestParam(required = false) String keyword,
                                @RequestParam(required = false) String status,
                                @RequestParam(required = false) String riskLevel,
                                @RequestParam(required = false) String type) {
        return contractService.listContracts(keyword, status, riskLevel, type);
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    @PostMapping("/contracts")
    public ContractMain createContract(@Valid @RequestBody ContractCreateRequest request) {
        return contractService.createContract(request);
    }

    @PutMapping("/contracts/{contractId}")
    public ResponseEntity<?> updateContract(@PathVariable Long contractId,
                                            @Valid @RequestBody ContractCreateRequest request) {
        ContractMain existing = contractService.findContract(contractId);
        if ("ARCHIVED".equals(existing.getStatus())) {
            return ResponseEntity.status(403).body(Map.of("message", "合同已归档锁定，不可编辑"));
        }
        return ResponseEntity.ok(contractService.updateContract(contractId, request));
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    @DeleteMapping("/contracts/{contractId}")
    public ResponseEntity<?> deleteContract(@PathVariable Long contractId) {
        try {
            contractService.deleteContract(contractId);
            return ResponseEntity.ok(Map.of("message", "草稿已删除"));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    // ==================== AI 起草 ====================

    /**
     * @deprecated 前端未使用，保留兼容旧接口。
     */
    @Deprecated
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    @PostMapping("/ai/draft")
    public AiDraftVO draft(@Valid @RequestBody AiDraftRequest request) {
        return contractService.generateDraft(request);
    }

    /**
     * @deprecated 前端未使用，保留兼容旧接口。
     */
    @Deprecated
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    @PostMapping("/ai/draft-stream")
    public SseEmitter draftStream(@Valid @RequestBody AiDraftRequest request) {
        return aiDraftService.streamDraft(request);
    }
}
