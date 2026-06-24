package cupk.smartcontract.service;

import cupk.smartcontract.dto.AiRiskReviewRequest;
import cupk.smartcontract.dto.ComplianceAiReviewResult;
import cupk.smartcontract.security.SensitiveDataMasker;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ComplianceAiService {
    public static final String AI_COMPLIANCE_NOTICE = "AI 内容仅供参考，需法务或业务负责人复核。";

    private final SensitiveDataMasker masker;

    public ComplianceAiService(SensitiveDataMasker masker) {
        this.masker = masker;
    }

    public ComplianceAiReviewResult reviewRisk(AiRiskReviewRequest request) {
        String sanitizedPrompt = buildSanitizedPrompt(request);
        masker.assertAiSafe(sanitizedPrompt);
        return new ComplianceAiReviewResult(
                AI_COMPLIANCE_NOTICE,
                sanitizedPrompt,
                "Qwen-compatible-placeholder",
                simulateModelReview(sanitizedPrompt)
        );
    }

    public String exportReviewText(ComplianceAiReviewResult result) {
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
            risks.add("付款条款包含银行账户占位符，需在线下核验账户权属。");
        }
        if (sanitizedPrompt.contains("[ID_CARD]") || sanitizedPrompt.contains("[MOBILE]") || sanitizedPrompt.contains("[EMAIL]")) {
            risks.add("个人敏感信息已在模型调用前移除，最终复核应使用内部可信记录。");
        }
        if (risks.isEmpty()) {
            risks.add("脱敏后的文本未发现明显演示风险。");
        }
        return risks;
    }
}
