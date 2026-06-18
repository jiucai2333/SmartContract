package cupk.smartcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cupk.smartcontract.dto.*;
import cupk.smartcontract.entity.*;
import cupk.smartcontract.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class PerformanceService {
    private static final BigDecimal DAILY_PENALTY_RATE = new BigDecimal("0.0005");
    private static final Set<String> PLAN_STATUSES =
            Set.of("PENDING", "PROCESSING", "IN_PROGRESS", "FULFILLED", "COMPLETED", "OVERDUE");

    private final DeliverableMapper deliverableMapper;
    private final PaymentPlanMapper paymentPlanMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final ContractMainMapper contractMapper;
    private final FulfillmentPlanMapper fulfillmentPlanMapper;

    public PerformanceService(DeliverableMapper deliverableMapper,
                              PaymentPlanMapper paymentPlanMapper,
                              PaymentRecordMapper paymentRecordMapper,
                              ContractMainMapper contractMapper,
                              FulfillmentPlanMapper fulfillmentPlanMapper) {
        this.deliverableMapper = deliverableMapper;
        this.paymentPlanMapper = paymentPlanMapper;
        this.paymentRecordMapper = paymentRecordMapper;
        this.contractMapper = contractMapper;
        this.fulfillmentPlanMapper = fulfillmentPlanMapper;
    }

    public List<DeliverableVO> listDeliverables(Long contractId) {
        return deliverableMapper.selectList(new LambdaQueryWrapper<Deliverable>()
                        .eq(Deliverable::getContractId, contractId)
                        .orderByAsc(Deliverable::getContractStage)
                        .orderByAsc(Deliverable::getSortOrder))
                .stream().map(this::toDeliverableVO).toList();
    }

    @Transactional
    public DeliverableVO saveDeliverable(DeliverableRequest request) {
        requireContractAndPlan(request.getContractId(), request.getPlanId());
        Deliverable item = request.getDeliverableId() == null
                ? new Deliverable() : requireDeliverable(request.getDeliverableId());
        if (item.getDeliverableId() == null) {
            item.setContractId(request.getContractId());
            item.setIsConfirmed(0);
            item.setCreatedAt(LocalDateTime.now());
        }
        item.setPlanId(request.getPlanId());
        item.setDeliverableType(request.getDeliverableType());
        item.setItemName(request.getItemName());
        item.setContractStage(request.getContractStage());
        item.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        item.setUpdatedAt(LocalDateTime.now());
        if (item.getDeliverableId() == null) deliverableMapper.insert(item);
        else deliverableMapper.updateById(item);
        return toDeliverableVO(item);
    }

    @Transactional
    public DeliverableVO confirmDeliverable(Long id, String confirmedBy) {
        Deliverable item = requireDeliverable(id);
        item.setIsConfirmed(1);
        item.setConfirmedAt(LocalDateTime.now());
        if (StringUtils.hasText(confirmedBy)) item.setConfirmedBy(confirmedBy.trim());
        item.setUpdatedAt(LocalDateTime.now());
        deliverableMapper.updateById(item);
        refreshPaymentStatuses(item.getContractId());
        return toDeliverableVO(item);
    }

    @Transactional
    public DeliverableVO unconfirmDeliverable(Long id) {
        Deliverable item = requireDeliverable(id);
        item.setIsConfirmed(0);
        item.setConfirmedAt(null);
        item.setConfirmedBy(null);
        item.setUpdatedAt(LocalDateTime.now());
        deliverableMapper.updateById(item);
        refreshPaymentStatuses(item.getContractId());
        return toDeliverableVO(item);
    }

    public void deleteDeliverable(Long id) {
        deliverableMapper.deleteById(id);
    }

    public List<PaymentPlanVO> listPaymentPlans(Long contractId) {
        refreshPaymentStatuses(contractId);
        return paymentPlanMapper.selectList(new LambdaQueryWrapper<PaymentPlan>()
                        .eq(PaymentPlan::getContractId, contractId)
                        .orderByAsc(PaymentPlan::getInstallmentNo))
                .stream().map(this::toPaymentPlanVO).toList();
    }

    @Transactional
    public PaymentPlanVO savePaymentPlan(PaymentPlanRequest request) {
        requireContractAndPlan(request.getContractId(), request.getPlanId());
        PaymentPlan plan = request.getPaymentPlanId() == null
                ? new PaymentPlan() : requirePaymentPlan(request.getPaymentPlanId());
        if (plan.getPaymentPlanId() == null) {
            plan.setContractId(request.getContractId());
            plan.setStatus("PENDING");
            plan.setCreatedAt(LocalDateTime.now());
        }
        plan.setPlanId(request.getPlanId());
        plan.setInstallmentNo(request.getInstallmentNo());
        plan.setRatio(request.getRatio());
        plan.setAmount(request.getAmount());
        plan.setDueDate(request.getDueDate());
        plan.setPrerequisiteDeliverableId(request.getPrerequisiteDeliverableId());
        plan.setUpdatedAt(LocalDateTime.now());
        if (plan.getPaymentPlanId() == null) paymentPlanMapper.insert(plan);
        else paymentPlanMapper.updateById(plan);
        updatePaymentStatus(plan);
        return toPaymentPlanVO(plan);
    }

    public void deletePaymentPlan(Long id) {
        paymentPlanMapper.deleteById(id);
    }

    public List<PaymentRecordVO> listPaymentRecords(Long contractId) {
        return paymentRecordMapper.selectList(new LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getContractId, contractId)
                        .orderByDesc(PaymentRecord::getPaidAt))
                .stream().map(this::toPaymentRecordVO).toList();
    }

    @Transactional
    public PaymentRecordVO recordPayment(PaymentRecordRequest request) {
        PaymentPlan plan = requirePaymentPlan(request.getPaymentPlanId());
        if (!Objects.equals(plan.getContractId(), request.getContractId())) {
            throw new IllegalArgumentException("付款计划与合同不匹配");
        }
        PaymentRecord record = new PaymentRecord();
        record.setPaymentPlanId(request.getPaymentPlanId());
        record.setContractId(request.getContractId());
        record.setPaidAmount(request.getPaidAmount());
        record.setPaidAt(request.getPaidAt());
        record.setReceiptNo(request.getReceiptNo());
        record.setNotes(request.getNotes());
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        paymentRecordMapper.insert(record);
        updatePaymentStatus(plan);
        return toPaymentRecordVO(record);
    }

    @Transactional
    public void deletePaymentRecord(Long id) {
        PaymentRecord record = paymentRecordMapper.selectById(id);
        if (record == null) return;
        paymentRecordMapper.deleteById(id);
        PaymentPlan plan = paymentPlanMapper.selectById(record.getPaymentPlanId());
        if (plan != null) updatePaymentStatus(plan);
    }

    public long getOverdueDays(Long id) {
        PaymentPlan plan = requirePaymentPlan(id);
        if ("PAID".equals(plan.getStatus()) || plan.getDueDate() == null
                || !LocalDate.now().isAfter(plan.getDueDate())) return 0;
        return ChronoUnit.DAYS.between(plan.getDueDate(), LocalDate.now());
    }

    public String getLiabilityHint(Long id) {
        PaymentPlan plan = requirePaymentPlan(id);
        return responsibility(plan, getOverdueDays(id));
    }

    public List<OverdueItemVO> listOverdueList() {
        List<PaymentPlan> plans = paymentPlanMapper.selectList(null);
        plans.forEach(this::updatePaymentStatus);
        return plans.stream().filter(p -> "OVERDUE".equals(p.getStatus()))
                .sorted((a, b) -> a.getDueDate().compareTo(b.getDueDate()))
                .map(this::toOverdueItemVO).toList();
    }

    public PerformanceProgressVO getProgress(Long contractId) {
        ContractMain contract = requireContract(contractId);
        List<Deliverable> deliverables = deliverableMapper.selectList(
                new LambdaQueryWrapper<Deliverable>().eq(Deliverable::getContractId, contractId));
        List<PaymentPlan> plans = paymentPlanMapper.selectList(
                new LambdaQueryWrapper<PaymentPlan>().eq(PaymentPlan::getContractId, contractId));
        plans.forEach(this::updatePaymentStatus);
        long confirmed = deliverables.stream().filter(d -> Integer.valueOf(1).equals(d.getIsConfirmed())).count();
        BigDecimal paid = plans.stream().map(p -> sumPaid(p.getPaymentPlanId()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = contract.getAmount() == null ? BigDecimal.ZERO : contract.getAmount();
        PerformanceProgressVO vo = new PerformanceProgressVO();
        vo.setTotalDeliverables(deliverables.size());
        vo.setConfirmedDeliverables(confirmed);
        vo.setDeliveryProgress(deliverables.isEmpty() ? 0 : (int) (confirmed * 100 / deliverables.size()));
        vo.setTotalPaymentPlans(plans.size());
        vo.setPaidPlans(plans.stream().filter(p -> "PAID".equals(p.getStatus())).count());
        vo.setOverduePlans(plans.stream().filter(p -> "OVERDUE".equals(p.getStatus())).count());
        vo.setTotalAmount(total);
        vo.setTotalPaid(paid);
        vo.setTotalUnpaid(total.subtract(paid).max(BigDecimal.ZERO));
        return vo;
    }

    public List<FulfillmentPlanVO> listFulfillmentPlans(Long contractId) {
        return fulfillmentPlanMapper.selectList(new LambdaQueryWrapper<FulfillmentPlan>()
                        .eq(FulfillmentPlan::getContractId, contractId)
                        .orderByAsc(FulfillmentPlan::getDueDate))
                .stream().map(this::toFulfillmentPlanVO).toList();
    }

    @Transactional
    public FulfillmentPlanVO updatePlanStatus(Long id, String status, String notes) {
        FulfillmentPlan plan = fulfillmentPlanMapper.selectById(id);
        if (plan == null) throw new IllegalArgumentException("履约节点不存在");
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!PLAN_STATUSES.contains(normalized)) throw new IllegalArgumentException("无效的履约状态");
        plan.setStatus(normalized);
        plan.setActualDate(("FULFILLED".equals(normalized) || "COMPLETED".equals(normalized))
                ? LocalDate.now() : null);
        if (notes != null) plan.setCompletionNotes(notes);
        plan.setUpdatedAt(LocalDateTime.now());
        fulfillmentPlanMapper.updateById(plan);
        return toFulfillmentPlanVO(plan);
    }

    public List<ContractSummaryVO> listActiveContracts() {
        return contractMapper.selectList(new LambdaQueryWrapper<ContractMain>()
                        .ne(ContractMain::getStatus, "DRAFT")
                        .orderByDesc(ContractMain::getCreatedAt))
                .stream().map(contract -> {
                    ContractSummaryVO vo = new ContractSummaryVO();
                    vo.setContractId(contract.getContractId());
                    vo.setContractNo(contract.getContractNo());
                    vo.setTitle(contract.getTitle());
                    vo.setAmount(contract.getAmount() == null ? BigDecimal.ZERO : contract.getAmount());
                    return vo;
                }).toList();
    }

    private void requireContractAndPlan(Long contractId, Long planId) {
        requireContract(contractId);
        FulfillmentPlan plan = fulfillmentPlanMapper.selectById(planId);
        if (plan == null || !Objects.equals(plan.getContractId(), contractId)) {
            throw new IllegalArgumentException("履约节点与合同不匹配");
        }
    }

    private ContractMain requireContract(Long id) {
        ContractMain contract = contractMapper.selectById(id);
        if (contract == null) throw new IllegalArgumentException("合同不存在");
        return contract;
    }

    private Deliverable requireDeliverable(Long id) {
        Deliverable item = deliverableMapper.selectById(id);
        if (item == null) throw new IllegalArgumentException("交付物不存在");
        return item;
    }

    private PaymentPlan requirePaymentPlan(Long id) {
        PaymentPlan plan = paymentPlanMapper.selectById(id);
        if (plan == null) throw new IllegalArgumentException("付款计划不存在");
        return plan;
    }

    private void refreshPaymentStatuses(Long contractId) {
        paymentPlanMapper.selectList(new LambdaQueryWrapper<PaymentPlan>()
                .eq(PaymentPlan::getContractId, contractId)).forEach(this::updatePaymentStatus);
    }

    private void updatePaymentStatus(PaymentPlan plan) {
        BigDecimal paid = sumPaid(plan.getPaymentPlanId());
        String status = paid.compareTo(plan.getAmount()) >= 0 ? "PAID"
                : plan.getDueDate() != null && LocalDate.now().isAfter(plan.getDueDate())
                ? "OVERDUE" : "PENDING";
        if (!status.equals(plan.getStatus())) {
            plan.setStatus(status);
            plan.setUpdatedAt(LocalDateTime.now());
            paymentPlanMapper.updateById(plan);
        }
    }

    private BigDecimal sumPaid(Long paymentPlanId) {
        return paymentRecordMapper.selectList(new LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getPaymentPlanId, paymentPlanId))
                .stream().map(PaymentRecord::getPaidAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String responsibility(PaymentPlan plan, long overdueDays) {
        if (overdueDays <= 0) return null;
        if (plan.getPrerequisiteDeliverableId() == null) return "甲方延迟支付";
        Deliverable item = deliverableMapper.selectById(plan.getPrerequisiteDeliverableId());
        return item != null && Integer.valueOf(1).equals(item.getIsConfirmed())
                ? "甲方延迟支付" : "待人工判断乙方履约责任";
    }

    private DeliverableVO toDeliverableVO(Deliverable item) {
        DeliverableVO vo = new DeliverableVO();
        vo.setDeliverableId(item.getDeliverableId());
        vo.setPlanId(item.getPlanId());
        vo.setContractId(item.getContractId());
        vo.setDeliverableType(item.getDeliverableType());
        vo.setDeliverableTypeName(DeliverableVO.typeName(item.getDeliverableType()));
        vo.setItemName(item.getItemName());
        vo.setContractStage(item.getContractStage());
        vo.setIsConfirmed(item.getIsConfirmed());
        vo.setConfirmedAt(item.getConfirmedAt());
        vo.setConfirmedBy(item.getConfirmedBy());
        vo.setSortOrder(item.getSortOrder());
        vo.setCreatedAt(item.getCreatedAt());
        vo.setUpdatedAt(item.getUpdatedAt());
        return vo;
    }

    private PaymentRecordVO toPaymentRecordVO(PaymentRecord record) {
        PaymentRecordVO vo = new PaymentRecordVO();
        vo.setRecordId(record.getRecordId());
        vo.setPaymentPlanId(record.getPaymentPlanId());
        vo.setContractId(record.getContractId());
        vo.setPaidAmount(record.getPaidAmount());
        vo.setPaidAt(record.getPaidAt());
        vo.setReceiptNo(record.getReceiptNo());
        vo.setNotes(record.getNotes());
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }

    private PaymentPlanVO toPaymentPlanVO(PaymentPlan plan) {
        PaymentPlanVO vo = new PaymentPlanVO();
        vo.setPaymentPlanId(plan.getPaymentPlanId());
        vo.setPlanId(plan.getPlanId());
        vo.setContractId(plan.getContractId());
        vo.setInstallmentNo(plan.getInstallmentNo());
        vo.setRatio(plan.getRatio());
        vo.setAmount(plan.getAmount());
        vo.setDueDate(plan.getDueDate());
        vo.setStatus(plan.getStatus());
        vo.setPrerequisiteDeliverableId(plan.getPrerequisiteDeliverableId());
        if (plan.getPrerequisiteDeliverableId() != null) {
            Deliverable item = deliverableMapper.selectById(plan.getPrerequisiteDeliverableId());
            vo.setPrerequisiteDeliverableName(item == null ? null : item.getItemName());
            vo.setPrerequisiteConfirmed(item != null && Integer.valueOf(1).equals(item.getIsConfirmed()));
        } else {
            vo.setPrerequisiteConfirmed(true);
        }
        BigDecimal paid = sumPaid(plan.getPaymentPlanId());
        long overdueDays = "PAID".equals(plan.getStatus()) || plan.getDueDate() == null
                || !LocalDate.now().isAfter(plan.getDueDate()) ? 0
                : ChronoUnit.DAYS.between(plan.getDueDate(), LocalDate.now());
        vo.setTotalPaid(paid);
        vo.setOverdueDays(overdueDays);
        vo.setPenaltyAmount(plan.getAmount().multiply(DAILY_PENALTY_RATE)
                .multiply(BigDecimal.valueOf(overdueDays)).setScale(2, RoundingMode.HALF_UP));
        vo.setResponsibilityHint(responsibility(plan, overdueDays));
        vo.setRecords(paymentRecordMapper.selectList(new LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getPaymentPlanId, plan.getPaymentPlanId())
                        .orderByDesc(PaymentRecord::getPaidAt))
                .stream().map(this::toPaymentRecordVO).toList());
        vo.setCreatedAt(plan.getCreatedAt());
        vo.setUpdatedAt(plan.getUpdatedAt());
        return vo;
    }

    private OverdueItemVO toOverdueItemVO(PaymentPlan plan) {
        ContractMain contract = contractMapper.selectById(plan.getContractId());
        PaymentPlanVO detail = toPaymentPlanVO(plan);
        OverdueItemVO vo = new OverdueItemVO();
        vo.setPaymentPlanId(plan.getPaymentPlanId());
        vo.setContractId(plan.getContractId());
        if (contract != null) {
            vo.setContractNo(contract.getContractNo());
            vo.setContractTitle(contract.getTitle());
        }
        vo.setInstallmentNo(plan.getInstallmentNo());
        vo.setRatio(plan.getRatio());
        vo.setAmount(plan.getAmount());
        vo.setDueDate(plan.getDueDate());
        vo.setOverdueDays(detail.getOverdueDays());
        vo.setPenaltyAmount(detail.getPenaltyAmount());
        vo.setResponsibilityHint(detail.getResponsibilityHint());
        vo.setPrerequisiteDeliverableName(detail.getPrerequisiteDeliverableName());
        vo.setPrerequisiteConfirmed(detail.getPrerequisiteConfirmed());
        vo.setTotalPaid(detail.getTotalPaid());
        return vo;
    }

    private FulfillmentPlanVO toFulfillmentPlanVO(FulfillmentPlan plan) {
        ContractMain contract = contractMapper.selectById(plan.getContractId());
        FulfillmentPlanVO vo = new FulfillmentPlanVO();
        vo.setPlanId(plan.getPlanId());
        vo.setContractId(plan.getContractId());
        vo.setMilestoneName(plan.getMilestoneName());
        vo.setDueDate(plan.getDueDate());
        vo.setActualDate(plan.getActualDate());
        vo.setStatus(plan.getStatus());
        vo.setStatusName(FulfillmentPlanVO.statusName(plan.getStatus()));
        vo.setCompletionNotes(plan.getCompletionNotes());
        if (contract != null) {
            vo.setContractNo(contract.getContractNo());
            vo.setContractTitle(contract.getTitle());
        }
        return vo;
    }
}
