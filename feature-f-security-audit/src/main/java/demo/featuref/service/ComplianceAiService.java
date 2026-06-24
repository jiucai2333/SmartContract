package demo.featuref.service;

import demo.featuref.dto.AiRiskReviewRequest;
import demo.featuref.dto.AiRiskReviewResult;
import demo.featuref.util.SensitiveDataMasker;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ComplianceAiService {
    public static final String AI_COMPLIANCE_NOTICE =
            "AI generated content is for reference only and must be reviewed by legal or business owners.";

    private final SensitiveDataMasker masker;

    public ComplianceAiService(SensitiveDataMasker masker) {
        this.masker = masker;
    }

    public AiRiskReviewResult reviewRisk(AiRiskReviewRequest request) {
        String sanitizedPrompt = buildSanitizedPrompt(request);
        masker.assertAiSafe(sanitizedPrompt);
        return new AiRiskReviewResult(
                AI_COMPLIANCE_NOTICE,
                sanitizedPrompt,
                "Qwen-compatible-placeholder",
                simulateModelReview(sanitizedPrompt)
        );
    }

    public String exportReviewText(AiRiskReviewResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append(result.complianceNotice()).append(System.lineSeparator());
        builder.append("Model: ").append(result.modelName()).append(System.lineSeparator());
        builder.append("Sanitized prompt: ").append(masker.maskForDisplay(result.sanitizedPrompt()))
                .append(System.lineSeparator());
        result.risks().forEach(risk -> builder.append("- ")
                .append(masker.maskForDisplay(risk)).append(System.lineSeparator()));
        return builder.toString();
    }

    private String buildSanitizedPrompt(AiRiskReviewRequest request) {
        String context = """
                Contract type: %s
                Party A: [COMPANY_A]
                Party B: [COMPANY_B]
                Business scope: %s
                Contract text:
                ---
                %s
                ---
                """.formatted(
                masker.maskForAi(Objects.toString(request.contractType(), "")),
                masker.maskForAi(Objects.toString(request.businessScope(), "")),
                masker.maskForAi(request.contractText())
        );
        return context.trim();
    }

    private List<String> simulateModelReview(String sanitizedPrompt) {
        List<String> risks = new ArrayList<>();
        if (sanitizedPrompt.contains("[BANK_ACCOUNT]")) {
            risks.add("Payment clause contains bank account placeholders; verify account ownership offline.");
        }
        if (sanitizedPrompt.contains("[ID_CARD]") || sanitizedPrompt.contains("[MOBILE]") || sanitizedPrompt.contains("[EMAIL]")) {
            risks.add("Personal data was removed before model review; use internal records for final verification.");
        }
        if (risks.isEmpty()) {
            risks.add("No obvious demo risk found in the sanitized text.");
        }
        return risks;
    }
}
