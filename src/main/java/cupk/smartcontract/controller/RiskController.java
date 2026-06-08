package cupk.smartcontract.controller;

import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.dto.AiRiskReviewResult;
import cupk.smartcontract.dto.AiRiskReviewRequest;
import cupk.smartcontract.service.ContractManagementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 风险审查页面（risk.html）Controller。
 */
@RestController
@RequestMapping("/api")
public class RiskController {

    private final ContractManagementService contractService;

    public RiskController(ContractManagementService contractService) {
        this.contractService = contractService;
    }

    @RequireRole({"LEGAL", "EXECUTIVE", "ADMIN"})
    @PostMapping("/ai/risk-review")
    public ResponseEntity<?> riskReview(@Valid @RequestBody AiRiskReviewRequest request) {
        try {
            AiRiskReviewResult result = contractService.aiRiskReview(request);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @RequireRole({"LEGAL", "EXECUTIVE", "ADMIN"})
    @GetMapping("/risk-reports")
    public Object listRiskReports(@RequestParam(required = false) Long contractId) {
        return contractService.listRiskReports(contractId);
    }

    @RequireRole({"LEGAL", "EXECUTIVE", "ADMIN"})
    @GetMapping("/risk-reports/{reportId}")
    public Object getRiskReport(@PathVariable Long reportId) {
        return contractService.getRiskReport(reportId);
    }

    /**
     * @deprecated 前端未使用，保留兼容旧接口。
     */
    @Deprecated
    @RequireRole({"DEPT_LEADER", "LEGAL", "EXECUTIVE", "ADMIN"})
    @GetMapping("/risks")
    public Object listRisks(@RequestParam(required = false) Long contractId) {
        return contractService.listRisks(contractId);
    }
}
