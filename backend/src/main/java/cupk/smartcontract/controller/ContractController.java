package cupk.smartcontract.controller;

import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.security.AuditOperation;
import cupk.smartcontract.entity.ContractMain;
import cupk.smartcontract.dto.ContractCreateRequest;
import cupk.smartcontract.service.ContractManagementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ContractController {
    private final ContractManagementService contractService;

    public ContractController(ContractManagementService contractService) {
        this.contractService = contractService;
    }

    @GetMapping("/contracts")
    public Object listContracts(@RequestParam(required = false) String keyword,
                                @RequestParam(required = false) String status,
                                @RequestParam(required = false) String riskLevel,
                                @RequestParam(required = false) String type,
                                @RequestParam(required = false)
                                @org.springframework.format.annotation.DateTimeFormat(
                                        iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                                java.time.LocalDate signDateFrom,
                                @RequestParam(required = false)
                                @org.springframework.format.annotation.DateTimeFormat(
                                        iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                                java.time.LocalDate signDateTo,
                                @RequestParam(required = false) java.math.BigDecimal amountMin,
                                @RequestParam(required = false) java.math.BigDecimal amountMax) {
        return contractService.listContracts(keyword, status, riskLevel, type,
                signDateFrom, signDateTo, amountMin, amountMax);
    }

    @GetMapping("/contracts/{contractId}")
    public ContractMain getContract(@PathVariable Long contractId) {
        contractService.assertCanAccess(contractId);
        return contractService.findContract(contractId);
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "FINANCE", "EXECUTIVE", "ADMIN"})
    @PostMapping("/contracts")
    @AuditOperation(operation = "CREATE_CONTRACT", targetType = "CONTRACT")
    public ContractMain createContract(@Valid @RequestBody ContractCreateRequest request) {
        return contractService.createContract(request);
    }

    @PutMapping("/contracts/{contractId}")
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "FINANCE", "EXECUTIVE", "ADMIN"})
    @AuditOperation(operation = "UPDATE_CONTRACT", targetType = "CONTRACT",
            targetIdParameter = "contractId")
    public ResponseEntity<?> updateContract(@PathVariable Long contractId,
                                            @Valid @RequestBody ContractCreateRequest request) {
        ContractMain existing = contractService.findContract(contractId);
        if ("ARCHIVED".equals(existing.getStatus())) {
            return ResponseEntity.status(403).body(Map.of("message", "合同已归档锁定，不可编辑"));
        }
        return ResponseEntity.ok(contractService.updateContract(contractId, request));
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "FINANCE", "EXECUTIVE", "ADMIN"})
    @DeleteMapping("/contracts/{contractId}")
    @AuditOperation(operation = "DELETE_CONTRACT", targetType = "CONTRACT",
            targetIdParameter = "contractId")
    public ResponseEntity<?> deleteContract(@PathVariable Long contractId) {
        try {
            contractService.deleteContract(contractId);
            return ResponseEntity.ok(Map.of("message", "草稿已删除"));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

}
