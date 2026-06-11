package cupk.smartcontract.dto;

import java.util.*;

public record AiDraftResponse(
        String complianceNotice,
        String draft,
        List<String> riskTips,
        String sanitizedPrompt
) {
}
