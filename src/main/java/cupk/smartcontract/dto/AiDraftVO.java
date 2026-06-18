package cupk.smartcontract.dto;

import java.util.*;

public record AiDraftVO(
        String complianceNotice,
        String draft,
        List<String> riskTips,
        String sanitizedPrompt
) {
}
