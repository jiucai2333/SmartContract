package cupk.smartcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cupk.smartcontract.common.SecurityContext;
import cupk.smartcontract.domain.ApprovalInstance;
import cupk.smartcontract.domain.ArchiveRecord;
import cupk.smartcontract.domain.ContractMain;
import cupk.smartcontract.domain.FulfillmentPlan;
import cupk.smartcontract.domain.RiskItem;
import cupk.smartcontract.domain.SealRecord;
import cupk.smartcontract.dto.ArchiveCreateRequest;
import cupk.smartcontract.dto.AiDraftRequest;
import cupk.smartcontract.dto.AiDraftResponse;
import cupk.smartcontract.dto.ContractCreateRequest;
import cupk.smartcontract.dto.DashboardSummary;
import cupk.smartcontract.dto.SealCreateRequest;
import cupk.smartcontract.mapper.ApprovalInstanceMapper;
import cupk.smartcontract.mapper.ContractMainMapper;
import cupk.smartcontract.mapper.FulfillmentPlanMapper;
import cupk.smartcontract.mapper.RiskItemMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ContractManagementService {

    @Value("${contract.threshold.major:100000}")
    private BigDecimal majorThreshold;

    @Value("${contract.threshold.super:500000}")
    private BigDecimal superThreshold;

    private final ContractMainMapper contractMapper;
    private final RiskItemMapper riskMapper;
    private final ApprovalInstanceMapper approvalMapper;
    private final FulfillmentPlanMapper planMapper;
    private final AiDraftService aiDraftService;
    private final StatusTransitionService statusTransitionService;

    public ContractManagementService(ContractMainMapper contractMapper,
                                     RiskItemMapper riskMapper,
                                     ApprovalInstanceMapper approvalMapper,
                                     FulfillmentPlanMapper planMapper,
                                     AiDraftService aiDraftService,
                                     StatusTransitionService statusTransitionService) {
        this.contractMapper = contractMapper;
        this.riskMapper = riskMapper;
        this.approvalMapper = approvalMapper;
        this.planMapper = planMapper;
        this.aiDraftService = aiDraftService;
        this.statusTransitionService = statusTransitionService;
    }

    // ==================== 数据范围过滤 ====================

    /**
     * 按当前用户 dataScope 过滤合同查询：
     * SELF → owner_id = currentUserId
     * DEPT → dept_id = currentDeptId
     * ALL  → 不过滤
     */
    private void applyDataScope(LambdaQueryWrapper<ContractMain> wrapper) {
        String scope = SecurityContext.dataScope();
        Long currentUserId = SecurityContext.userId();
        Long currentDeptId = SecurityContext.deptId();
        if ("SELF".equals(scope) && currentUserId != null) {
            wrapper.eq(ContractMain::getOwnerId, currentUserId);
        } else if ("DEPT".equals(scope) && currentDeptId != null) {
            wrapper.eq(ContractMain::getDeptId, currentDeptId);
        }
    }

    /**
     * 履约计划数据范围过滤（通过 contract_id 子查询）。
     */
    private void applyPlanDataScope(LambdaQueryWrapper<FulfillmentPlan> wrapper) {
        String scope = SecurityContext.dataScope();
        Long currentUserId = SecurityContext.userId();
        Long currentDeptId = SecurityContext.deptId();
        if ("SELF".equals(scope) && currentUserId != null) {
            wrapper.inSql(FulfillmentPlan::getContractId,
                    "SELECT contract_id FROM contract_main WHERE owner_id = " + currentUserId);
        } else if ("DEPT".equals(scope) && currentDeptId != null) {
            wrapper.inSql(FulfillmentPlan::getContractId,
                    "SELECT contract_id FROM contract_main WHERE dept_id = " + currentDeptId);
        }
    }

    /**
     * 风险项数据范围过滤（通过 contract_id 子查询）。
     */
    private void applyRiskDataScope(LambdaQueryWrapper<RiskItem> wrapper) {
        String scope = SecurityContext.dataScope();
        Long currentUserId = SecurityContext.userId();
        Long currentDeptId = SecurityContext.deptId();
        if ("SELF".equals(scope) && currentUserId != null) {
            wrapper.inSql(RiskItem::getContractId,
                    "SELECT contract_id FROM contract_main WHERE owner_id = " + currentUserId);
        } else if ("DEPT".equals(scope) && currentDeptId != null) {
            wrapper.inSql(RiskItem::getContractId,
                    "SELECT contract_id FROM contract_main WHERE dept_id = " + currentDeptId);
        }
    }

    // ==================== 仪表盘 ====================

    public DashboardSummary dashboard() {
        List<ContractMain> contracts = listContracts(null, null, null);
        List<FulfillmentPlan> plans = listPlans();
        long approving = contracts.stream().filter(c -> "APPROVING".equals(c.getStatus())).count();
        long highRisk = contracts.stream().filter(c -> "HIGH".equals(c.getRiskLevel())).count();
        long dueSoon = plans.stream()
                .filter(p -> !"FULFILLED".equals(p.getStatus()))
                .filter(p -> !p.getDueDate().isAfter(LocalDate.now().plusDays(30)))
                .count();
        BigDecimal totalAmount = contracts.stream()
                .map(ContractMain::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DashboardSummary(
                contracts.size(), approving, highRisk, dueSoon, totalAmount,
                distribution(contracts, ContractMain::getStatus),
                distribution(contracts, ContractMain::getRiskLevel));
    }

    // ==================== 合同 CRUD ====================

    public List<ContractMain> listContracts(String keyword, String status, String riskLevel) {
        LambdaQueryWrapper<ContractMain> wrapper = new LambdaQueryWrapper<ContractMain>()
                .and(StringUtils.hasText(keyword), w -> w.like(ContractMain::getTitle, keyword)
                        .or().like(ContractMain::getCounterparty, keyword)
                        .or().like(ContractMain::getContractNo, keyword))
                .eq(StringUtils.hasText(status), ContractMain::getStatus, status)
                .eq(StringUtils.hasText(riskLevel), ContractMain::getRiskLevel, riskLevel)
                .orderByDesc(ContractMain::getCreatedAt);
        applyDataScope(wrapper);
        return contractMapper.selectList(wrapper);
    }

    public ContractMain createContract(ContractCreateRequest request) {
        ContractMain contract = new ContractMain();
        contract.setContractNo("HT-" + LocalDate.now().getYear()
                + "-" + (System.currentTimeMillis() % 100000));
        contract.setTitle(request.title());
        contract.setType(request.type());
        contract.setAmount(request.amount());
        contract.setCounterparty(request.counterparty());
        contract.setDeptId(request.deptId());
        contract.setOwnerId(request.ownerId());
        contract.setStatus("DRAFT");
        contract.setRiskLevel(autoRiskLevel(request.amount()));
        contract.setDueDate(request.dueDate());
        contract.setCreatedBy(SecurityContext.roleCode() + "_" + SecurityContext.userId());
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        contract.setDeleted(0);
        contract.setVersion(1);
        contractMapper.insert(contract);
        return contract;
    }

    public ContractMain updateContract(Long contractId, ContractCreateRequest request) {
        ContractMain contract = findContract(contractId);
        contract.setTitle(request.title());
        contract.setType(request.type());
        contract.setAmount(request.amount());
        contract.setCounterparty(request.counterparty());
        contract.setDeptId(request.deptId());
        contract.setOwnerId(request.ownerId());
        contract.setDueDate(request.dueDate());
        contract.setRiskLevel(autoRiskLevel(request.amount()));
        contract.setUpdatedAt(LocalDateTime.now());
        contractMapper.updateById(contract);
        return contract;
    }

    public ContractMain findContract(Long contractId) {
        ContractMain contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw new IllegalArgumentException("合同不存在");
        }
        return contract;
    }

    // ==================== 审批链 ====================

    /**
     * 提交审批，根据合同金额自动判断审批链类型：
     * NORMAL → DeptLeader 单审
     * MAJOR  → DeptLeader → Legal + Executive 会签
     * SUPER  → DeptLeader → Legal + Executive 同时会签（缺一不可）
     */
    public ContractMain submitApproval(Long contractId) {
        ContractMain contract = findContract(contractId);
        if ("HIGH".equals(contract.getRiskLevel())) {
            throw new IllegalStateException("存在未经复核的高风险问题，已阻断提交审批。");
        }
        contract.setStatus("APPROVING");
        contract.setUpdatedAt(LocalDateTime.now());
        contractMapper.updateById(contract);

        String flowType = determineFlowType(contract.getAmount());
        ApprovalInstance instance = new ApprovalInstance();
        instance.setContractId(contractId);
        instance.setFlowType(flowType);
        instance.setCurrentNode("部门主管审批");
        instance.setStatus("RUNNING");
        instance.setStartedAt(LocalDateTime.now());
        instance.setCreatedBy("system");
        instance.setCreatedAt(LocalDateTime.now());
        instance.setDeleted(0);
        instance.setVersion(1);
        approvalMapper.insert(instance);
        return contract;
    }

    private String determineFlowType(BigDecimal amount) {
        if (amount.compareTo(superThreshold) >= 0) return "SUPER";
        if (amount.compareTo(majorThreshold) >= 0) return "MAJOR";
        return "NORMAL";
    }

    // ==================== 风险 / 审批 / 履约 ====================

    public List<RiskItem> listRisks(Long contractId) {
        LambdaQueryWrapper<RiskItem> wrapper = new LambdaQueryWrapper<RiskItem>()
                .eq(contractId != null, RiskItem::getContractId, contractId)
                .orderByDesc(RiskItem::getCreatedAt);
        applyRiskDataScope(wrapper);
        return riskMapper.selectList(wrapper);
    }

    public List<ApprovalInstance> listApprovals() {
        return approvalMapper.selectList(
                new LambdaQueryWrapper<ApprovalInstance>().orderByDesc(ApprovalInstance::getStartedAt));
    }

    public List<FulfillmentPlan> listPlans() {
        LambdaQueryWrapper<FulfillmentPlan> wrapper =
                new LambdaQueryWrapper<FulfillmentPlan>().orderByAsc(FulfillmentPlan::getDueDate);
        applyPlanDataScope(wrapper);
        return planMapper.selectList(wrapper);
    }

    // ==================== AI 起草 ====================

    public AiDraftResponse generateDraft(AiDraftRequest request) {
        return aiDraftService.generateDraft(request);
    }

    // ==================== 辅助 ====================

    private String autoRiskLevel(BigDecimal amount) {
        if (amount.compareTo(new BigDecimal("500000")) > 0) return "HIGH";
        if (amount.compareTo(new BigDecimal("50000")) > 0) return "MEDIUM";
        return "LOW";
    }

    private List<Map<String, Object>> distribution(
            List<ContractMain> contracts,
            java.util.function.Function<ContractMain, String> classifier) {
        return contracts.stream()
                .collect(Collectors.groupingBy(
                        c -> Objects.toString(classifier.apply(c), "UNKNOWN"),
                        LinkedHashMap::new, Collectors.counting()))
                .entrySet()
                .stream()
                .map(e -> Map.<String, Object>of("name", e.getKey(), "value", e.getValue()))
                .toList();
    }

    // ==================== 签章 & 归档 ====================

    public SealRecord seal(SealCreateRequest request) { return statusTransitionService.seal(request); }
    public ArchiveRecord archive(ArchiveCreateRequest request) { return statusTransitionService.archive(request); }
    public List<SealRecord> listSealRecords(Long contractId) { return statusTransitionService.listSealRecords(contractId); }
    public List<ArchiveRecord> listArchiveRecords(Long contractId) { return statusTransitionService.listArchiveRecords(contractId); }
    public boolean isVersionLocked(Long versionId) { return statusTransitionService.isVersionLocked(versionId); }
    public Set<Long> getLockedVersionIds(Long contractId) { return statusTransitionService.getLockedVersionIds(contractId); }
    public StatusTransitionService getStatusTransitionService() { return statusTransitionService; }
    public ContractMainMapper getContractMapper() { return contractMapper; }
}
