package cupk.smartcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cupk.smartcontract.entity.FulfillmentPlan;
import cupk.smartcontract.mapper.FulfillmentPlanMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class FulfillmentService {
    private static final Set<String> STATUSES =
            Set.of("PENDING", "PROCESSING", "FULFILLED", "OVERDUE");

    private final FulfillmentPlanMapper mapper;

    public FulfillmentService(FulfillmentPlanMapper mapper) {
        this.mapper = mapper;
    }

    public List<FulfillmentPlan> listPlans() {
        List<FulfillmentPlan> plans = mapper.selectList(new LambdaQueryWrapper<FulfillmentPlan>()
                .orderByAsc(FulfillmentPlan::getDueDate));
        plans.forEach(plan -> {
            if (!"FULFILLED".equals(plan.getStatus())
                    && plan.getDueDate() != null
                    && plan.getDueDate().isBefore(LocalDate.now())) {
                plan.setStatus("OVERDUE");
            }
        });
        return plans;
    }

    @Transactional
    public FulfillmentPlan updateStatus(Long planId, String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("无效的履约状态");
        }
        FulfillmentPlan plan = mapper.selectById(planId);
        if (plan == null) {
            throw new IllegalArgumentException("履约计划不存在");
        }
        plan.setStatus(normalized);
        plan.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(plan);
        return plan;
    }
}
