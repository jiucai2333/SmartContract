package cupk.smartcontract.dto;

public record AiRiskVO(
        String category,
        String level,
        String clause,
        String reason,
        String suggestion
) {
}
