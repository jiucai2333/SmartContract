package cupk.smartcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cupk.smartcontract.dto.*;
import cupk.smartcontract.entity.*;
import cupk.smartcontract.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PerformanceService {

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

    // ==================== Deliverables ====================

    public List<DeliverableVO> listDeliverables(Long contractId) {
        List<Deliverable> deliverables = deliverableMapper.selectList(
                new LambdaQueryWrapper<Deliverable>()
                        .eq(Deliverable::getContractId, contractId)
                        .orderByAsc(Deliverable::getContractStage)
                        .orderByAsc(Deliverable::getSortOrder));
        return deliverables.stream().map(this::toDeliverableVO).toList();
    }

    @Transactional
    public DeliverableVO saveDeliverable(DeliverableRequest req) {
        if (req.getDeliverableId() != null) {
            Deliverable existing = deliverableMapper.selectById(req.getDeliverableId());
            if (existing == null) throw new IllegalArgumentException("交付物不存在");
            existing.setPlanId(req.getPlanId());
            existing.setDeliverableType(req.getDeliverableType());
            existing.setItemName(req.getItemName());
            existing.setContractStage(req.getContractStage());
            existing.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
            existing.setUpdatedAt(LocalDateTime.now());
            deliverableMapper.updateById(existing);
            return toDeliverableVO(existing);
        }
        Deliverable d = new Deliverable();
        d.setPlanId(req.getPlanId());
        d.setContractId(req.getContractId());
        d.setDeliverableType(req.getDeliverableType());
        d.setItemName(req.getItemName());
        d.setContractStage(req.getContractStage());
        d.setIsConfirmed(0);
        d.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        d.setCreatedAt(LocalDateTime.now());
        d.setUpdatedAt(LocalDateTime.now());
        deliverableMapper.insert(d);
        return toDeliverableVO(d);
    }

    @Transactional
    public DeliverableVO confirmDeliverable(Long deliverableId, String confirmedBy) {
        Deliverable d = deliverableMapper.selectById(deliverableId);
        if (d == null) throw new IllegalArgumentException("交付物不存在");
        d.setIsConfirmed(1);
        d.setConfirmedAt(LocalDateTime.now());
        d.setConfirmedBy(StringUtils.hasText(confirmedBy) ? confirmedBy : d.getConfirmedBy());
        d.setUpdatedAt(LocalDateTime.now());
        deliverableMapper.updateById(d);
        updatePaymentPlanOverdueStatus(d.getContractId());
        return toDeliverableVO(d);
    }

    @Transactional
    public DeliverableVO unconfirmDeliverable(Long deliverableId) {
        Deliverable d = deliverableMapper.selectById(deliverableId);
        if (d == null) throw new IllegalArgumentException("交付物不存在");
        d.setIsConfirmed(0);
        d.setConfirmedAt(null);
        d.setConfirmedBy(null);
        d.setUpdatedAt(LocalDateTime.now());
        deliverableMapper.updateById(d);
        updatePaymentPlanOverdueStatus(d.getContractId());
        return toDeliverableVO(d);
    }

    public void deleteDeliverable(Long deliverableId) {
        deliverableMapper.deleteById(deliverableId);
    }

    // ==================== Payment Plans ====================

    public List<PaymentPlanVO> listPaymentPlans(Long contractId) {
        List<PaymentPlan> plans = paymentPlanMapper.selectList(
                new LambdaQueryWrapper<PaymentPlan>()
                        .eq(PaymentPlan::getContractId, contractId)
                        .orderByAsc(PaymentPlan::getInstallmentNo));
        return plans.stream().map(p -> toPaymentPlanVO(p)).toList();
    }

    @Transactional
    public PaymentPlanVO savePaymentPlan(PaymentPlanRequest req) {
        if (req.getPaymentPlanId() != null) {
            PaymentPlan existing = paymentPlanMapper.selectById(req.getPaymentPlanId());
            if (existing == null) throw new IllegalArgumentException("付款计划不存在");
            existing.setPlanId(req.getPlanId());
            existing.setInstallmentNo(req.getInstallmentNo());
            existing.setRatio(req.getRatio());
            existing.setAmount(req.getAmount());
            existing.setDueDate(req.getDueDate());
            existing.setPrerequisiteDeliverableId(req.getPrerequisiteDeliverableId());
            existing.setUpdatedAt(LocalDateTime.now());
            paymentPlanMapper.updateById(existing);
            updatePaymentPlanOverdueStatus(req.getContractId());
            return toPaymentPlanVO(existing);
        }
        PaymentPlan p = new PaymentPlan();
        p.setPlanId(req.getPlanId());
        p.setContractId(req.getContractId());
        p.setInstallmentNo(req.getInstallmentNo());
        p.setRatio(req.getRatio());
        p.setAmount(req.getAmount());
        p.setDueDate(req.getDueDate());
        p.setStatus("PENDING");
        p.setPrerequisiteDeliverableId(req.getPrerequisiteDeliverableId());
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        paymentPlanMapper.insert(p);
        return toPaymentPlanVO(p);
    }

    public void deletePaymentPlan(Long paymentPlanId) {
        paymentPlanMapper.deleteById(paymentPlanId);
    }

    // ==================== Payment Records ====================

    public List<PaymentRecordVO> listPaymentRecords(Long contractId) {
        List<PaymentRecord> records = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getContractId, contractId)
                        .orderByDesc(PaymentRecord::getPaidAt));
        return records.stream().map(this::toPaymentRecordVO).toList();
    }

    @Transactional
    public PaymentRecordVO recordPayment(PaymentRecordRequest req) {
        PaymentPlan plan = paymentPlanMapper.selectById(req.getPaymentPlanId());
        if (plan == null) throw new IllegalArgumentException("付款计划不存在");

        PaymentRecord record = new PaymentRecord();
        record.setPaymentPlanId(req.getPaymentPlanId());
        record.setContractId(req.getContractId());
        record.setPaidAmount(req.getPaidAmount());
        record.setPaidAt(req.getPaidAt() != null ? req.getPaidAt() : LocalDateTime.now());
        record.setReceiptNo(req.getReceiptNo());
        record.setNotes(req.getNotes());
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        paymentRecordMapper.insert(record);

        updatePlanStatusAfterPayment(req.getPaymentPlanId(), req.getContractId());
        return toPaymentRecordVO(record);
    }

    public void deletePaymentRecord(Long recordId) {
        PaymentRecord record = paymentRecordMapper.selectById(recordId);
        if (record != null) {
            paymentRecordMapper.deleteById(recordId);
            updatePlanStatusAfterPayment(record.getPaymentPlanId(), record.getContractId());
        }
    }

    // ==================== Overdue & Liability ====================

    public long getOverdueDays(Long paymentPlanId) {
        PaymentPlan plan = paymentPlanMapper.selectById(paymentPlanId);
        if (plan == null || plan.getDueDate() == null || "PAID".equals(plan.getStatus())) return 0;
        if (!LocalDate.now().isAfter(plan.getDueDate())) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(plan.getDueDate(), LocalDate.now());
    }

    public String getLiabilityHint(Long paymentPlanId) {
        PaymentPlan plan = paymentPlanMapper.selectById(paymentPlanId);
        if (plan == null) return null;
        long overdueDays = getOverdueDays(paymentPlanId);
        if (overdueDays <= 0) return null;

        if (plan.getPrerequisiteDeliverableId() == null) {
            return "⚠ 甲方延迟支付";
        }
        Deliverable prereq = deliverableMapper.selectById(plan.getPrerequisiteDeliverableId());
        if (prereq != null && prereq.getIsConfirmed() == 1) {
            return "⚠ 甲方延迟支付";
        }
        return "⚠ 待人工判断乙方履约责任";
    }

    public List<OverdueItemVO> listOverdueList() {
        List<PaymentPlan> allPlans = paymentPlanMapper.selectList(null);
        // Refresh overdue statuses
        for (PaymentPlan plan : allPlans) {
            if (!"PAID".equals(plan.getStatus())
                    && plan.getDueDate() != null
                    && LocalDate.now().isAfter(plan.getDueDate())) {
                plan.setStatus("OVERDUE");
                plan.setUpdatedAt(LocalDateTime.now());
                paymentPlanMapper.updateById(plan);
            }
        }
        // Reload only overdue
        List<PaymentPlan> overduePlans = paymentPlanMapper.selectList(
                new LambdaQueryWrapper<PaymentPlan>()
                        .eq(PaymentPlan::getStatus, "OVERDUE")
                        .orderByAsc(PaymentPlan::getDueDate));
        return overduePlans.stream().map(this::toOverdueItemVO).toList();
    }

    // ==================== Fulfillment Plans ====================

    public List<FulfillmentPlanVO> listFulfillmentPlans(Long contractId) {
        List<FulfillmentPlan> plans = fulfillmentPlanMapper.selectList(
                new LambdaQueryWrapper<FulfillmentPlan>()
                        .eq(FulfillmentPlan::getContractId, contractId)
                        .eq(FulfillmentPlan::getIsDeleted, 0)
                        .orderByAsc(FulfillmentPlan::getDueDate));
        return plans.stream().map(this::toFulfillmentPlanVO).toList();
    }

    @Transactional
    public FulfillmentPlanVO updatePlanStatus(Long planId, String status, String notes) {
        FulfillmentPlan plan = fulfillmentPlanMapper.selectById(planId);
        if (plan == null) throw new IllegalArgumentException("履约节点不存在");
        plan.setStatus(status);
        if ("COMPLETED".equals(status)) {
            plan.setActualDate(LocalDate.now());
        }
        if (notes != null) {
            plan.setCompletionNotes(notes);
        }
        plan.setUpdatedAt(LocalDateTime.now());
        fulfillmentPlanMapper.updateById(plan);
        return toFulfillmentPlanVO(plan);
    }

    public List<ContractSummaryVO> listActiveContracts() {
        List<ContractMain> contracts = contractMapper.selectList(
                new LambdaQueryWrapper<ContractMain>()
                        .ne(ContractMain::getStatus, "DRAFT")
                        .orderByDesc(ContractMain::getCreatedAt));
        return contracts.stream().map(c -> {
            ContractSummaryVO vo = new ContractSummaryVO();
            vo.setContractId(c.getContractId());
            vo.setContractNo(c.getContractNo());
            vo.setTitle(c.getTitle());
            vo.setAmount(c.getAmount() != null ? c.getAmount() : BigDecimal.ZERO);
            return vo;
        }).toList();
    }

    // ==================== Overview ====================

    public PerformanceProgressVO getProgress(Long contractId) {
        ContractMain contract = contractMapper.selectById(contractId);
        if (contract == null) throw new IllegalArgumentException("合同不存在");

        List<Deliverable> deliverables = deliverableMapper.selectList(
                new LambdaQueryWrapper<Deliverable>().eq(Deliverable::getContractId, contractId));
        List<PaymentPlan> plans = paymentPlanMapper.selectList(
                new LambdaQueryWrapper<PaymentPlan>().eq(PaymentPlan::getContractId, contractId));

        long totalDeliverables = deliverables.size();
        long confirmedDeliverables = deliverables.stream().filter(d -> d.getIsConfirmed() == 1).count();
        long totalPaymentPlans = plans.size();

        updatePaymentPlanOverdueStatus(contractId);
        plans = paymentPlanMapper.selectList(
                new LambdaQueryWrapper<PaymentPlan>().eq(PaymentPlan::getContractId, contractId));
        long paidPlans = plans.stream().filter(p -> "PAID".equals(p.getStatus())).count();
        long overduePlans = plans.stream().filter(p -> "OVERDUE".equals(p.getStatus())).count();

        BigDecimal totalAmount = contract.getAmount() != null ? contract.getAmount() : BigDecimal.ZERO;
        BigDecimal totalPaid = plans.stream()
                .map(p -> sumPaidForPlan(p.getPaymentPlanId()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PerformanceProgressVO vo = new PerformanceProgressVO();
        vo.setTotalDeliverables(totalDeliverables);
        vo.setConfirmedDeliverables(confirmedDeliverables);
        vo.setDeliveryProgress(totalDeliverables > 0
                ? (int) ((confirmedDeliverables * 100) / totalDeliverables) : 0);
        vo.setTotalPaymentPlans(totalPaymentPlans);
        vo.setPaidPlans(paidPlans);
        vo.setOverduePlans(overduePlans);
        vo.setTotalAmount(totalAmount);
        vo.setTotalPaid(totalPaid);
        vo.setTotalUnpaid(totalAmount.subtract(totalPaid));
        return vo;
    }

    // ==================== Private helpers ====================

    private FulfillmentPlanVO toFulfillmentPlanVO(FulfillmentPlan fp) {
        FulfillmentPlanVO vo = new FulfillmentPlanVO();
        vo.setPlanId(fp.getPlanId());
        vo.setContractId(fp.getContractId());
        vo.setMilestoneName(fp.getMilestoneName());
        vo.setDueDate(fp.getDueDate());
        vo.setActualDate(fp.getActualDate());
        vo.setStatus(fp.getStatus());
        vo.setStatusName(FulfillmentPlanVO.statusName(fp.getStatus()));
        vo.setCompletionNotes(fp.getCompletionNotes());

        ContractMain contract = contractMapper.selectById(fp.getContractId());
        if (contract != null) {
            vo.setContractNo(contract.getContractNo());
            vo.setContractTitle(contract.getTitle());
        }
        return vo;
    }

    private DeliverableVO toDeliverableVO(Deliverable d) {
        DeliverableVO vo = new DeliverableVO();
        vo.setDeliverableId(d.getDeliverableId());
        vo.setPlanId(d.getPlanId());
        vo.setContractId(d.getContractId());
        vo.setDeliverableType(d.getDeliverableType());
        vo.setDeliverableTypeName(DeliverableVO.typeName(d.getDeliverableType()));
        vo.setItemName(d.getItemName());
        vo.setContractStage(d.getContractStage());
        vo.setIsConfirmed(d.getIsConfirmed());
        vo.setConfirmedAt(d.getConfirmedAt());
        vo.setConfirmedBy(d.getConfirmedBy());
        vo.setSortOrder(d.getSortOrder());
        vo.setCreatedAt(d.getCreatedAt());
        vo.setUpdatedAt(d.getUpdatedAt());
        return vo;
    }

    private PaymentPlanVO toPaymentPlanVO(PaymentPlan p) {
        PaymentPlanVO vo = new PaymentPlanVO();
        vo.setPaymentPlanId(p.getPaymentPlanId());
        vo.setPlanId(p.getPlanId());
        vo.setContractId(p.getContractId());
        vo.setInstallmentNo(p.getInstallmentNo());
        vo.setRatio(p.getRatio());
        vo.setAmount(p.getAmount());
        vo.setDueDate(p.getDueDate());
        vo.setStatus(p.getStatus());
        vo.setPrerequisiteDeliverableId(p.getPrerequisiteDeliverableId());
        vo.setCreatedAt(p.getCreatedAt());
        vo.setUpdatedAt(p.getUpdatedAt());

        if (p.getPrerequisiteDeliverableId() != null) {
            Deliverable prereq = deliverableMapper.selectById(p.getPrerequisiteDeliverableId());
            if (prereq != null) {
                vo.setPrerequisiteDeliverableName(prereq.getItemName());
                vo.setPrerequisiteConfirmed(prereq.getIsConfirmed() == 1);
            } else {
                vo.setPrerequisiteConfirmed(false);
            }
        } else {
            vo.setPrerequisiteConfirmed(true);
        }

        vo.setTotalPaid(sumPaidForPlan(p.getPaymentPlanId()));

        List<PaymentRecord> records = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getPaymentPlanId, p.getPaymentPlanId())
                        .orderByDesc(PaymentRecord::getPaidAt));
        vo.setRecords(records.stream().map(this::toPaymentRecordVO).toList());

        vo.calculateOverdue();
        vo.determineResponsibility();
        return vo;
    }

    private OverdueItemVO toOverdueItemVO(PaymentPlan p) {
        OverdueItemVO vo = new OverdueItemVO();
        vo.setPaymentPlanId(p.getPaymentPlanId());
        vo.setContractId(p.getContractId());

        // Load contract info
        ContractMain contract = contractMapper.selectById(p.getContractId());
        if (contract != null) {
            vo.setContractNo(contract.getContractNo());
            vo.setContractTitle(contract.getTitle());
        }

        vo.setInstallmentNo(p.getInstallmentNo());
        vo.setRatio(p.getRatio());
        vo.setAmount(p.getAmount());
        vo.setDueDate(p.getDueDate());
        vo.setTotalPaid(sumPaidForPlan(p.getPaymentPlanId()));

        // Overdue days
        long overdueDays = 0;
        BigDecimal penalty = BigDecimal.ZERO;
        if (p.getDueDate() != null && LocalDate.now().isAfter(p.getDueDate()) && !"PAID".equals(p.getStatus())) {
            overdueDays = java.time.temporal.ChronoUnit.DAYS.between(p.getDueDate(), LocalDate.now());
            penalty = p.getAmount().multiply(new BigDecimal("0.0005"))
                    .multiply(BigDecimal.valueOf(overdueDays))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        }
        vo.setOverdueDays(overdueDays);
        vo.setPenaltyAmount(penalty);

        // Liability hint
        if (overdueDays > 0) {
            if (p.getPrerequisiteDeliverableId() == null) {
                vo.setResponsibilityHint("⚠ 甲方延迟支付");
                vo.setPrerequisiteConfirmed(true);
            } else {
                Deliverable prereq = deliverableMapper.selectById(p.getPrerequisiteDeliverableId());
                if (prereq != null) {
                    vo.setPrerequisiteDeliverableName(prereq.getItemName());
                    vo.setPrerequisiteConfirmed(prereq.getIsConfirmed() == 1);
                    vo.setResponsibilityHint(prereq.getIsConfirmed() == 1
                            ? "⚠ 甲方延迟支付"
                            : "⚠ 待人工判断乙方履约责任");
                } else {
                    vo.setPrerequisiteConfirmed(false);
                    vo.setResponsibilityHint("⚠ 待人工判断乙方履约责任");
                }
            }
        }
        return vo;
    }

    private PaymentRecordVO toPaymentRecordVO(PaymentRecord r) {
        PaymentRecordVO vo = new PaymentRecordVO();
        vo.setRecordId(r.getRecordId());
        vo.setPaymentPlanId(r.getPaymentPlanId());
        vo.setContractId(r.getContractId());
        vo.setPaidAmount(r.getPaidAmount());
        vo.setPaidAt(r.getPaidAt());
        vo.setReceiptNo(r.getReceiptNo());
        vo.setNotes(r.getNotes());
        vo.setCreatedAt(r.getCreatedAt());
        return vo;
    }

    private BigDecimal sumPaidForPlan(Long paymentPlanId) {
        List<PaymentRecord> records = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getPaymentPlanId, paymentPlanId));
        return records.stream()
                .map(PaymentRecord::getPaidAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void updatePlanStatusAfterPayment(Long paymentPlanId, Long contractId) {
        PaymentPlan plan = paymentPlanMapper.selectById(paymentPlanId);
        if (plan == null) return;
        BigDecimal totalPaid = sumPaidForPlan(paymentPlanId);

        if (totalPaid.compareTo(plan.getAmount()) >= 0) {
            plan.setStatus("PAID");
        } else {
            if (plan.getDueDate() != null && LocalDate.now().isAfter(plan.getDueDate())) {
                plan.setStatus("OVERDUE");
            } else {
                plan.setStatus("PENDING");
            }
        }
        plan.setUpdatedAt(LocalDateTime.now());
        paymentPlanMapper.updateById(plan);
    }

    private void updatePaymentPlanOverdueStatus(Long contractId) {
        List<PaymentPlan> plans = paymentPlanMapper.selectList(
                new LambdaQueryWrapper<PaymentPlan>().eq(PaymentPlan::getContractId, contractId));
        for (PaymentPlan plan : plans) {
            if (!"PAID".equals(plan.getStatus())
                    && plan.getDueDate() != null
                    && LocalDate.now().isAfter(plan.getDueDate())) {
                plan.setStatus("OVERDUE");
                plan.setUpdatedAt(LocalDateTime.now());
                paymentPlanMapper.updateById(plan);
            }
        }
    }
}
