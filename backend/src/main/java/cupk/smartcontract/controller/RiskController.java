package cupk.smartcontract.controller;

import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.security.AuditOperation;
import cupk.smartcontract.dto.AiRiskReviewResult;
import cupk.smartcontract.dto.AiRiskReviewRequest;
import cupk.smartcontract.service.ContractManagementService;
import cupk.smartcontract.service.RiskReportExportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    private final RiskReportExportService riskReportExportService;

    public RiskController(ContractManagementService contractService,
                          RiskReportExportService riskReportExportService) {
        this.contractService = contractService;
        this.riskReportExportService = riskReportExportService;
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "FINANCE", "EXECUTIVE", "ADMIN"})
    @PostMapping("/ai/risk-review")
    @AuditOperation(operation = "RISK_REVIEW", targetType = "CONTRACT")
    public ResponseEntity<?> riskReview(@Valid @RequestBody AiRiskReviewRequest request) {
        try {
            AiRiskReviewResult result = contractService.aiRiskReview(request);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "FINANCE", "EXECUTIVE", "ADMIN"})
    @GetMapping("/risk-reports")
    @AuditOperation(operation = "RISK_REPORT_LIST", targetType = "RISK_REPORT")
    public Object listRiskReports(@RequestParam(required = false) Long contractId) {
        return contractService.listRiskReports(contractId);
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "FINANCE", "EXECUTIVE", "ADMIN"})
    @GetMapping("/risk-reports/{reportId}")
    @AuditOperation(operation = "RISK_REPORT_VIEW", targetType = "RISK_REPORT",
            targetIdParameter = "reportId")
    public Object getRiskReport(@PathVariable Long reportId) {
        return contractService.getRiskReport(reportId);
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "FINANCE", "EXECUTIVE", "ADMIN"})
    @GetMapping("/risk-reports/{reportId}/export")
    @AuditOperation(operation = "RISK_REPORT_EXPORT", targetType = "RISK_REPORT",
            targetIdParameter = "reportId")
    public ResponseEntity<byte[]> exportRiskReport(@PathVariable Long reportId) {
        RiskReportExportService.ExportFile export = riskReportExportService.exportDocx(reportId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + export.encodedFilename())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(export.bytes());
    }

    /**
     * @deprecated 前端未使用，保留兼容旧接口。
     */
    @Deprecated
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "FINANCE", "EXECUTIVE", "ADMIN"})
    @GetMapping("/risks")
    public Object listRisks(@RequestParam(required = false) Long contractId) {
        return contractService.listRisks(contractId);
    }
}
