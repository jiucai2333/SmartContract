package cupk.smartcontract.dto;

import java.util.*;

public record DraftTemplateAnalysis(
        String markdown,
        List<DraftField> fields,
        String analysisMode,
        String notice
) {
    public record DraftField(
            String key,
            String label,
            String value,
            String placeholder,
            String inputType,
            boolean required,
            String sourceHint
    ) {
    }
}
