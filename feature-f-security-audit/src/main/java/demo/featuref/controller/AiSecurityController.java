package demo.featuref.controller;

import demo.featuref.common.Result;
import demo.featuref.common.RoleCode;
import demo.featuref.dto.AiRiskReviewRequest;
import demo.featuref.dto.AiRiskReviewResult;
import demo.featuref.security.AuditOperation;
import demo.featuref.security.RequireRole;
import demo.featuref.service.ComplianceAiService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiSecurityController {
    private final ComplianceAiService complianceAiService;

    public AiSecurityController(ComplianceAiService complianceAiService) {
        this.complianceAiService = complianceAiService;
    }

    @PostMapping("/risk-review")
    @RequireRole({RoleCode.LEGAL, RoleCode.EXECUTIVE, RoleCode.ADMIN})
    @AuditOperation(operation = "AI_RISK_REVIEW", targetType = "CONTRACT")
    public Result<AiRiskReviewResult> riskReview(@Valid @RequestBody AiRiskReviewRequest request) {
        return Result.success(complianceAiService.reviewRisk(request));
    }

    @PostMapping("/risk-review/export")
    @RequireRole({RoleCode.LEGAL, RoleCode.EXECUTIVE, RoleCode.ADMIN})
    @AuditOperation(operation = "AI_RISK_REVIEW_EXPORT", targetType = "CONTRACT")
    public Result<String> exportReview(@Valid @RequestBody AiRiskReviewRequest request) {
        return Result.success(complianceAiService.exportReviewText(complianceAiService.reviewRisk(request)));
    }
}
