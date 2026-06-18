package cupk.smartcontract.controller;

import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.security.AuditOperation;
import cupk.smartcontract.entity.ContractMain;
import cupk.smartcontract.dto.AuthUserVO;
import cupk.smartcontract.dto.RoleVO;
import cupk.smartcontract.dto.UserAdminVO;
import cupk.smartcontract.service.AuthService;
import cupk.smartcontract.service.ContractManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequireRole({"ADMIN"})
public class AdminController {

    private final AuthService authService;
    private final ContractManagementService contractService;

    public AdminController(AuthService authService, ContractManagementService contractService) {
        this.authService = authService;
        this.contractService = contractService;
    }

    @GetMapping("/users")
    public List<UserAdminVO> listUsers() {
        return authService.listUsers();
    }

    /**
     * 设置用户的唯一角色（单角色体系）。
     * Body: { "roleCode": "LEGAL" }
     */
    @PutMapping("/users/{userId}/role")
    @AuditOperation(operation = "ADMIN_UPDATE_ROLE", targetType = "USER",
            targetIdParameter = "userId")
    public AuthUserVO updateRole(@PathVariable Long userId,
                                  @RequestBody Map<String, String> body) {
        String roleCode = body.getOrDefault("roleCode", "USER");
        return authService.updateUserRole(userId, roleCode);
    }

    @GetMapping("/roles")
    public List<RoleVO> listRoles() {
        return authService.listRoles();
    }

    @PutMapping("/contracts/{contractId}/fields")
    @AuditOperation(operation = "ADMIN_EDIT", targetType = "CONTRACT",
            targetIdParameter = "contractId")
    public ResponseEntity<?> updateContractFields(@PathVariable Long contractId,
                                                  @RequestBody Map<String, String> body) {
        try {
            ContractMain contract = contractService.findContract(contractId);
            if (body.containsKey("status") && body.get("status") != null) {
                String target = body.get("status").trim();
                if (!target.equals(contract.getStatus())) {
                    contractService.getStatusTransitionService().transition(contract, target);
                }
            }
            if (body.containsKey("riskLevel") && body.get("riskLevel") != null) {
                contract.setRiskLevel(body.get("riskLevel").trim());
                contract.setUpdatedAt(LocalDateTime.now());
                contractService.getContractMapper().updateById(contract);
            }
            return ResponseEntity.ok(Map.of("message", "更新成功"));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}
