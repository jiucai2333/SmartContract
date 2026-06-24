package cupk.smartcontract.dto;

public record AiRiskVO(
        String level,
        String category,
        String clause,
        String reason,
        String suggestion,
        String replacement
) {
}
