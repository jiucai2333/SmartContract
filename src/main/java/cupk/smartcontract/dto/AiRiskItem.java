package cupk.smartcontract.dto;

public record AiRiskItem(
        String level,
        String clause,
        String reason,
        String suggestion
) {
}
