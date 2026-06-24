package cupk.smartcontract.dto;

import java.util.List;

public record ContractFieldAnalysisVO(
        List<ContractField> fields,
        int requiredMissingCount,
        int beforeSubmitMissingCount,
        String analysisMode,
        String notice
) {
    public record ContractField(
            String fieldKey,
            String fieldName,
            String fieldType,
            String requiredLevel,
            String placeholderText,
            String suggestedValue,
            String sourceText,
            double confidence,
            String status,
            String locatorType,
            Integer blockIndex,
            String fieldRole,
            Integer placeholderIndex,
            String anchorBefore,
            String anchorAfter
    ) {
    }
}
