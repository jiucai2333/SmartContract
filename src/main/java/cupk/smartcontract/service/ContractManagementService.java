package cupk.smartcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cupk.smartcontract.security.SecurityContext;
import cupk.smartcontract.entity.ContractMain;
import cupk.smartcontract.dto.AiDraftRequest;
import cupk.smartcontract.dto.AiDraftResponse;
import cupk.smartcontract.dto.ContractCreateRequest;
import cupk.smartcontract.dto.DashboardSummary;
import cupk.smartcontract.mapper.ContractMainMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ContractManagementService {

    private final ContractMainMapper contractMapper;
    private final AiDraftService aiDraftService;

    public ContractManagementService(ContractMainMapper contractMapper,
                                     AiDraftService aiDraftService) {
        this.contractMapper = contractMapper;
        this.aiDraftService = aiDraftService;
    }

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

    public List<ContractMain> listContracts(String keyword, String status, String riskLevel, String type) {
        LambdaQueryWrapper<ContractMain> wrapper = new LambdaQueryWrapper<ContractMain>()
                .and(StringUtils.hasText(keyword), w -> w.like(ContractMain::getTitle, keyword)
                        .or().like(ContractMain::getCounterparty, keyword)
                        .or().like(ContractMain::getContractNo, keyword))
                .eq(StringUtils.hasText(status), ContractMain::getStatus, status)
                .eq(StringUtils.hasText(type), ContractMain::getType, type)
                .orderByDesc(ContractMain::getCreatedAt);
        if (!"DRAFT".equals(status)) {
            wrapper.ne(ContractMain::getStatus, "DRAFT");
        }
        applyDataScope(wrapper);
        List<ContractMain> contracts = contractMapper.selectList(wrapper);
        // riskLevel is computed in memory since the column doesn't exist in DB
        contracts.forEach(c -> c.setRiskLevel(autoRiskLevel(c.getAmount())));
        if (StringUtils.hasText(riskLevel)) {
            contracts = contracts.stream()
                    .filter(c -> riskLevel.equals(c.getRiskLevel()))
                    .toList();
        }
        return contracts;
    }

    public DashboardSummary dashboardSummary() {
        List<ContractMain> contracts = contractMapper.selectList(new LambdaQueryWrapper<ContractMain>()
                        .ne(ContractMain::getStatus, "DRAFT"))
                .stream()
                .filter(this::canAccess)
                .toList();

        // Compute riskLevel in memory since the column doesn't exist in DB
        contracts.forEach(c -> c.setRiskLevel(autoRiskLevel(c.getAmount())));

        long totalContracts = contracts.size();
        long approvingContracts = contracts.stream().filter(c -> "APPROVING".equals(c.getStatus())).count();
        long highRiskContracts = contracts.stream().filter(c -> "HIGH".equals(c.getRiskLevel())).count();
        long dueSoonPlans = contracts.stream()
                .filter(c -> c.getDueDate() != null
                        && !c.getDueDate().isBefore(LocalDate.now())
                        && !c.getDueDate().isAfter(LocalDate.now().plusDays(30)))
                .count();
        BigDecimal totalAmount = contracts.stream()
                .map(ContractMain::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DashboardSummary(totalContracts, approvingContracts, highRiskContracts, dueSoonPlans,
                totalAmount, distribution(contracts, true), distribution(contracts, false));
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
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        contract.setDeleted(0);
        contractMapper.insert(contract);
        return contract;
    }

    public ContractMain updateContract(Long contractId, ContractCreateRequest request) {
        assertCanAccess(contractId);
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

    public AiDraftResponse generateDraft(AiDraftRequest request) {
        return aiDraftService.generateDraft(request);
    }

    private String autoRiskLevel(BigDecimal amount) {
        if (amount == null) return "LOW";
        if (amount.compareTo(new BigDecimal("500000")) > 0) return "HIGH";
        if (amount.compareTo(new BigDecimal("50000")) > 0) return "MEDIUM";
        return "LOW";
    }

    private List<Map<String, Object>> distribution(List<ContractMain> contracts, boolean byStatus) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ContractMain contract : contracts) {
            String key = byStatus ? contract.getStatus() : contract.getRiskLevel();
            counts.merge(StringUtils.hasText(key) ? key : "UNKNOWN", 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .map(e -> Map.<String, Object>of("name", e.getKey(), "value", e.getValue()))
                .toList();
    }
}
