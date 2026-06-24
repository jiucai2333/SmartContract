package cupk.smartcontract.controller;

import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.security.AuditOperation;
import cupk.smartcontract.dto.SealCreateRequest;
import cupk.smartcontract.service.ContractManagementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 签章登记页（seal.html）专属 Controller。
 */
@RestController
@RequestMapping("/api")
public class SealController {

    private final ContractManagementService contractService;

    public SealController(ContractManagementService contractService) {
        this.contractService = contractService;
    }

    @RequireRole({"LEGAL", "DEPT_LEADER", "ADMIN"})
    @PostMapping("/contracts/{contractId}/seal")
    @AuditOperation(operation = "SEAL_REGISTER", targetType = "CONTRACT",
            targetIdParameter = "contractId")
    public ResponseEntity<?> seal(@PathVariable Long contractId,
                                  @Valid @RequestBody SealCreateRequest request) {
        try {
            return ResponseEntity.ok(contractService.seal(request));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}
