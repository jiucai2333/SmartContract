package cupk.smartcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cupk.smartcontract.security.SecurityContext;
import cupk.smartcontract.entity.Approval;
import cupk.smartcontract.entity.ArchiveRecord;
import cupk.smartcontract.entity.ContractMain;
import cupk.smartcontract.entity.FulfillmentPlan;
import cupk.smartcontract.entity.RiskItem;
import cupk.smartcontract.entity.SealRecord;
import cupk.smartcontract.dto.ArchiveCreateRequest;
import cupk.smartcontract.dto.AiDraftRequest;
import cupk.smartcontract.dto.AiDraftVO;
import cupk.smartcontract.dto.AiRiskReviewRequest;
import cupk.smartcontract.dto.AiRiskVO;
import cupk.smartcontract.dto.ContractCreateRequest;
import cupk.smartcontract.dto.DashboardVO;
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

    // ==================== 数据权限 ====================

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

    public DashboardVO dashboard() {
        List<ContractMain> contracts = listContracts(null, null, null, null);
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
        return new DashboardVO(
                contracts.size(), approving, highRisk, dueSoon, totalAmount,
                distribution(contracts, ContractMain::getStatus),
                distribution(contracts, ContractMain::getRiskLevel));
    }

    public DashboardVO dashboardSummary() {
        return dashboard();
    }

    // ==================== 合同 CRUD ====================

    public List<ContractMain> listContracts(String keyword, String status, String riskLevel, String type) {
        LambdaQueryWrapper<ContractMain> wrapper = new LambdaQueryWrapper<ContractMain>()
                .and(StringUtils.hasText(keyword), w -> w.like(ContractMain::getTitle, keyword)
                        .or().like(ContractMain::getCounterparty, keyword)
                        .or().like(ContractMain::getContractNo, keyword))
                .eq(StringUtils.hasText(status), ContractMain::getStatus, status)
                .eq(StringUtils.hasText(type), ContractMain::getType, type)
                .orderByDesc(ContractMain::getCreatedAt);
        applyDataScope(wrapper);
        List<ContractMain> contracts = contractMapper.selectList(wrapper);
        if (StringUtils.hasText(riskLevel)) {
            contracts = contracts.stream()
                    .filter(c -> riskLevel.equals(c.getRiskLevel()))
                    .toList();
        }
        return contracts;
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
        contract.setSignDate(request.signDate());
        contract.setDueDate(request.dueDate());
        contract.setCreatedBy(SecurityContext.username());
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        contract.setDeleted(0);
        contractMapper.insert(contract);
        return contract;
    }

    public ContractMain updateContract(Long contractId, ContractCreateRequest request) {
        assertCanAccess(contractId);
        ContractMain contract = findContract(contractId);
        if ("ARCHIVED".equals(contract.getStatus())) {
            throw new IllegalStateException("合同已归档锁定，不可编辑");
        }
        contract.setTitle(request.title());
        contract.setType(request.type());
        contract.setAmount(request.amount());
        contract.setCounterparty(request.counterparty());
        contract.setDeptId(request.deptId());
        contract.setOwnerId(request.ownerId());
        contract.setSignDate(request.signDate());
        contract.setDueDate(request.dueDate());
        contract.setRiskLevel(autoRiskLevel(request.amount()));
        contract.setUpdatedBy(SecurityContext.username());
        contract.setUpdatedAt(LocalDateTime.now());
        contractMapper.updateById(contract);
        return contract;
    }

    public void deleteContract(Long contractId) {
        ContractMain contract = findContract(contractId);
        assertCanAccess(contractId);
        if (Integer.valueOf(1).equals(contract.getDeleted())) {
            throw new IllegalArgumentException("合同已删除");
        }
        if (!"DRAFT".equals(contract.getStatus())) {
            throw new IllegalStateException("仅草稿状态的合同可以删除");
        }
        contract.setDeleted(1);
        contract.setUpdatedAt(LocalDateTime.now());
        contractMapper.updateById(contract);
        statusTransitionService.writeLog(SecurityContext.userId(), "DELETE", "CONTRACT", contractId, "SUCCESS");
    }

    public ContractMain findContract(Long contractId) {
        ContractMain contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw new IllegalArgumentException("合同不存在");
        }
        return contract;
    }

    public void assertCanAccess(Long contractId) {
        if (!canAccess(findContract(contractId))) {
            throw new SecurityException("无权访问该合同");
        }
    }

    public boolean canAccess(ContractMain contract) {
        if (contract == null) {
            return false;
        }
        String scope = SecurityContext.dataScope();
        Long currentUserId = SecurityContext.userId();
        Long currentDeptId = SecurityContext.deptId();
        if ("SELF".equals(scope)) {
            return currentUserId != null && currentUserId.equals(contract.getOwnerId());
        }
        if ("DEPT".equals(scope)) {
            return currentDeptId != null && currentDeptId.equals(contract.getDeptId());
        }
        return true;
    }

    // ==================== 审批 ====================

    public ContractMain submitApproval(Long contractId) {
        ContractMain contract = findContract(contractId);
        if ("HIGH".equals(contract.getRiskLevel())) {
            throw new IllegalStateException("存在未复核的高风险问题，已阻断提交审批");
        }
        contract.setStatus("APPROVING");
        contract.setUpdatedAt(LocalDateTime.now());
        contractMapper.updateById(contract);

        String flowType = determineFlowType(contract.getAmount());
        Approval instance = new Approval();
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
        if (amount == null) return "NORMAL";
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

    public List<AiRiskVO> aiRiskReview(AiRiskReviewRequest request) {
        if (request.contractId() != null) {
            assertCanAccess(request.contractId());
        }
        List<AiRiskVO> risks = aiDraftService.analyzeRisks(request);
        if (request.contractId() != null) {
            persistAiRisks(request.contractId(), request.versionId(), risks);
        }
        return risks;
    }

    private void persistAiRisks(Long contractId, Long versionId, List<AiRiskVO> risks) {
        Long resolvedVersionId = versionId == null ? 0L : versionId;
        riskMapper.delete(new LambdaQueryWrapper<RiskItem>()
                .eq(RiskItem::getContractId, contractId)
                .eq(RiskItem::getVersionId, resolvedVersionId)
                .eq(RiskItem::getReviewStatus, "AI_PENDING"));

        LocalDateTime now = LocalDateTime.now();
        for (AiRiskVO risk : risks) {
            RiskItem item = new RiskItem();
            item.setContractId(contractId);
            item.setVersionId(resolvedVersionId);
            item.setClauseRef(clip(risk.clause(), 255));
            item.setRiskType("AI_REVIEW");
            item.setRiskLevel(normalizeRiskLevel(risk.level()));
            item.setSuggestion(buildRiskSuggestion(risk));
            item.setReviewStatus("AI_PENDING");
            item.setCreatedAt(now);
            riskMapper.insert(item);
        }

        ContractMain contract = findContract(contractId);
        contract.setRiskLevel(highestRiskLevel(risks));
        contract.setUpdatedBy(SecurityContext.username());
        contract.setUpdatedAt(now);
        contractMapper.updateById(contract);
    }

    public List<Approval> listApprovals() {
        return approvalMapper.selectList(
                new LambdaQueryWrapper<Approval>().orderByDesc(Approval::getStartedAt));
    }

    public List<FulfillmentPlan> listPlans() {
        LambdaQueryWrapper<FulfillmentPlan> wrapper =
                new LambdaQueryWrapper<FulfillmentPlan>().orderByAsc(FulfillmentPlan::getDueDate);
        applyPlanDataScope(wrapper);
        return planMapper.selectList(wrapper);
    }

    // ==================== AI 起草 ====================

    public AiDraftVO generateDraft(AiDraftRequest request) {
        return aiDraftService.generateDraft(request);
    }

    // ==================== 签章与归档 ====================

    public SealRecord seal(SealCreateRequest request) { return statusTransitionService.seal(request); }
    public ArchiveRecord archive(ArchiveCreateRequest request) { return statusTransitionService.archive(request); }
    public List<SealRecord> listSealRecords(Long contractId) { return statusTransitionService.listSealRecords(contractId); }
    public List<ArchiveRecord> listArchiveRecords(Long contractId) { return statusTransitionService.listArchiveRecords(contractId); }
    public boolean isVersionLocked(Long versionId) { return statusTransitionService.isVersionLocked(versionId); }
    public Set<Long> getLockedVersionIds(Long contractId) { return statusTransitionService.getLockedVersionIds(contractId); }
    public StatusTransitionService getStatusTransitionService() { return statusTransitionService; }
    public ContractMainMapper getContractMapper() { return contractMapper; }

    // ==================== 工具方法 ====================

    private String autoRiskLevel(BigDecimal amount) {
        if (amount == null) return "LOW";
        if (amount.compareTo(new BigDecimal("500000")) > 0) return "HIGH";
        if (amount.compareTo(new BigDecimal("50000")) > 0) return "MEDIUM";
        return "LOW";
    }

    private String highestRiskLevel(List<AiRiskVO> risks) {
        if (risks.stream().anyMatch(r -> "HIGH".equals(normalizeRiskLevel(r.level())))) return "HIGH";
        if (risks.stream().anyMatch(r -> "MEDIUM".equals(normalizeRiskLevel(r.level())))) return "MEDIUM";
        return "LOW";
    }

    private String normalizeRiskLevel(String level) {
        if (!StringUtils.hasText(level)) return "LOW";
        String normalized = level.trim().toUpperCase();
        if ("HIGH".equals(normalized) || "MEDIUM".equals(normalized) || "LOW".equals(normalized)) {
            return normalized;
        }
        if (normalized.contains("高")) return "HIGH";
        if (normalized.contains("中")) return "MEDIUM";
        return "LOW";
    }

    private String clip(String value, int max) {
        if (!StringUtils.hasText(value)) return "AI";
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private String buildRiskSuggestion(AiRiskVO risk) {
        String reason = StringUtils.hasText(risk.reason()) ? risk.reason().trim() : "AI 未返回详细原因";
        String suggestion = StringUtils.hasText(risk.suggestion()) ? risk.suggestion().trim() : "请法务复核该风险项";
        return "风险原因：" + reason + "\n修改建议：" + suggestion;
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
}
