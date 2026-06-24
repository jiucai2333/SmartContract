package cupk.smartcontract.dto;

public record AiRiskVO(
        String level,
        String clause,
        String reason,
        String suggestion
) {
}
