package cupk.smartcontract.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DashboardSummary(
        long totalContracts,
        long approvingContracts,
        long highRiskContracts,
        long dueSoonPlans,
        BigDecimal totalAmount,
        List<Map<String, Object>> statusDistribution,
        List<Map<String, Object>> riskDistribution
) {
}
