package cupk.smartcontract.dto;

public record FulfillmentStats(
        long totalPlans,
        long warningPlans,
        long overduePlans,
        long completedPlans,
        long handledOverdue,
        long reminderRecords
) {
}
