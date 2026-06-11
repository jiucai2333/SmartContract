package cupk.smartcontract.controller;

import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.entity.ArchiveRecord;
import cupk.smartcontract.entity.SealRecord;
import cupk.smartcontract.service.ContractManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 合同台账页（ledger.html）专属 Controller。
 * GET/POST /api/contracts /api/approvals 复用在 ContractController/ApprovalController 中，
 * 本 Controller 只提供台账页独有的端点。
 */
@RestController
@RequestMapping("/api")
public class LedgerController {

    private final ContractManagementService contractService;

    public LedgerController(ContractManagementService contractService) {
        this.contractService = contractService;
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    @PostMapping("/contracts/{contractId}/submit")
    public ResponseEntity<?> submit(@PathVariable Long contractId) {
        try {
            return ResponseEntity.ok(contractService.submitApproval(contractId));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/contracts/{contractId}/seal-records")
    public List<SealRecord> sealRecords(@PathVariable Long contractId) {
        return contractService.listSealRecords(contractId);
    }

    @GetMapping("/contracts/{contractId}/archive-records")
    public List<ArchiveRecord> archiveRecords(@PathVariable Long contractId) {
        return contractService.listArchiveRecords(contractId);
    }
}
