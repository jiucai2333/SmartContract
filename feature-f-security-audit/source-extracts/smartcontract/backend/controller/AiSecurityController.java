package cupk.smartcontract.controller;

import cupk.smartcontract.dto.AiRiskReviewRequest;
import cupk.smartcontract.dto.AiRiskReviewResult;
import cupk.smartcontract.security.AuditOperation;
import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.service.AiSecurityBoundaryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * F module boundary for AI security.
 * Business modules call this layer before contract text reaches any model provider.
 */
@RestController
@RequestMapping("/api/ai")
public class AiSecurityController {
    private final AiSecurityBoundaryService aiSecurityBoundaryService;

    public AiSecurityController(AiSecurityBoundaryService aiSecurityBoundaryService) {
        this.aiSecurityBoundaryService = aiSecurityBoundaryService;
    }

    @RequireRole({"LEGAL", "EXECUTIVE", "ADMIN"})
    @PostMapping("/risk-review")
    @AuditOperation(operation = "AI_RISK_REVIEW", targetType = "CONTRACT")
    public ResponseEntity<AiRiskReviewResult> riskReview(@Valid @RequestBody AiRiskReviewRequest request) {
        return ResponseEntity.ok(aiSecurityBoundaryService.reviewWithMaskedPrompt(request));
    }
}
