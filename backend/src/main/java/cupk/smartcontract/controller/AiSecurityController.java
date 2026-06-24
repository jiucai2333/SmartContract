package cupk.smartcontract.controller;

import cupk.smartcontract.common.Result;
import cupk.smartcontract.dto.AiRiskReviewRequest;
import cupk.smartcontract.dto.ComplianceAiReviewResult;
import cupk.smartcontract.security.AuditOperation;
import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.service.ComplianceAiService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/security")
public class AiSecurityController {
    private final ComplianceAiService complianceAiService;

    public AiSecurityController(ComplianceAiService complianceAiService) {
        this.complianceAiService = complianceAiService;
    }

    @PostMapping("/risk-review")
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "FINANCE", "EXECUTIVE", "ADMIN"})
    @AuditOperation(operation = "AI_RISK_REVIEW", targetType = "CONTRACT")
    public Result riskReview(@Valid @RequestBody AiRiskReviewRequest request) {
        ComplianceAiReviewResult result = complianceAiService.reviewRisk(request);
        return Result.success(result);
    }

    @PostMapping("/risk-review/export")
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "FINANCE", "EXECUTIVE", "ADMIN"})
    @AuditOperation(operation = "AI_RISK_REVIEW_EXPORT", targetType = "CONTRACT")
    public Result exportReview(@Valid @RequestBody AiRiskReviewRequest request) {
        return Result.success(complianceAiService.exportReviewText(complianceAiService.reviewRisk(request)));
    }
}
