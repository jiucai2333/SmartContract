package cupk.smartcontract.service;

import cupk.smartcontract.dto.AiRiskReviewRequest;
import cupk.smartcontract.dto.AiRiskReviewResult;
import cupk.smartcontract.security.SensitiveDataMasker;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * F module security boundary for model calls.
 * The important rule is: raw contract text must never cross this boundary.
 */
@Service
public class AiSecurityBoundaryService {
    public static final String AI_COMPLIANCE_NOTICE =
            "AI generated content is for reference only and must be reviewed by legal or business owners.";

    private final SensitiveDataMasker sensitiveDataMasker;

    public AiSecurityBoundaryService(SensitiveDataMasker sensitiveDataMasker) {
        this.sensitiveDataMasker = sensitiveDataMasker;
    }

    public AiRiskReviewResult reviewWithMaskedPrompt(AiRiskReviewRequest request) {
        String sanitizedPrompt = buildSanitizedPrompt(request);
        sensitiveDataMasker.assertAiSafe(sanitizedPrompt);

        // Replace this placeholder with the real model client in the owning business module.
        return new AiRiskReviewResult(
                AI_COMPLIANCE_NOTICE,
                sanitizedPrompt,
                "model-call-placeholder",
                List.of()
        );
    }

    private String buildSanitizedPrompt(AiRiskReviewRequest request) {
        String prompt = """
                Contract type: %s
                Party A: [COMPANY_A]
                Party B: [COMPANY_B]
                Business scope: %s
                Contract text:
                ---
                %s
                ---
                """.formatted(
                sensitiveDataMasker.maskForAi(Objects.toString(request.contractType(), "")),
                sensitiveDataMasker.maskForAi(Objects.toString(request.businessScope(), "")),
                sensitiveDataMasker.maskForAi(Objects.toString(request.contractText(), ""))
        );
        return prompt.trim();
    }
}
