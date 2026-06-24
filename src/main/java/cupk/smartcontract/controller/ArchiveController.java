package cupk.smartcontract.controller;

import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.security.AuditOperation;
import cupk.smartcontract.dto.ArchiveCreateRequest;
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
 * 归档确认页（archive.html）专属 Controller。
 */
@RestController
@RequestMapping("/api")
public class ArchiveController {

    private final ContractManagementService contractService;

    public ArchiveController(ContractManagementService contractService) {
        this.contractService = contractService;
    }

    @RequireRole({"LEGAL", "DEPT_LEADER", "ADMIN"})
    @PostMapping("/contracts/{contractId}/archive")
    @AuditOperation(operation = "ARCHIVE_CONFIRM", targetType = "CONTRACT",
            targetIdParameter = "contractId")
    public ResponseEntity<?> archive(@PathVariable Long contractId,
                                     @Valid @RequestBody ArchiveCreateRequest request) {
        try {
            return ResponseEntity.ok(contractService.archive(request));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}
