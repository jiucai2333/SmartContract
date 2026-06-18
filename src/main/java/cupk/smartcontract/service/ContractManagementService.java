package cupk.smartcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cupk.smartcontract.security.SecurityContext;
import cupk.smartcontract.entity.Approval;
import cupk.smartcontract.entity.ApprovalRecord;
import cupk.smartcontract.entity.ArchiveRecord;
import cupk.smartcontract.entity.ContractAttachment;
import cupk.smartcontract.entity.ContractMain;
import cupk.smartcontract.entity.FulfillmentPlan;
import cupk.smartcontract.entity.RiskItem;
import cupk.smartcontract.entity.RiskReport;
import cupk.smartcontract.entity.SealRecord;
import cupk.smartcontract.dto.ArchiveCreateRequest;
import cupk.smartcontract.dto.AiDraftRequest;
import cupk.smartcontract.dto.AiDraftVO;
import cupk.smartcontract.dto.AiRiskReviewRequest;
import cupk.smartcontract.dto.AiRiskReviewResult;
import cupk.smartcontract.dto.AiRiskVO;
import cupk.smartcontract.dto.ContractCreateRequest;
import cupk.smartcontract.dto.DashboardVO;
import cupk.smartcontract.dto.RiskReportVO;
import cupk.smartcontract.dto.SealCreateRequest;
import cupk.smartcontract.mapper.ApprovalInstanceMapper;
import cupk.smartcontract.mapper.ApprovalRecordMapper;
import cupk.smartcontract.mapper.ContractAttachmentMapper;
import cupk.smartcontract.mapper.ContractMainMapper;
import cupk.smartcontract.mapper.FulfillmentPlanMapper;
import cupk.smartcontract.mapper.RiskItemMapper;
import cupk.smartcontract.mapper.RiskReportMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final ContractAttachmentMapper attachmentMapper;
    private final ContractNumberService contractNumberService;
    private final RiskItemMapper riskMapper;
    private final RiskReportMapper riskReportMapper;
    private final ApprovalInstanceMapper approvalMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final FulfillmentPlanMapper planMapper;
    private final AiDraftService aiDraftService;
    private final StatusTransitionService statusTransitionService;

    public ContractManagementService(ContractMainMapper contractMapper,
                                     ContractAttachmentMapper attachmentMapper,
                                     ContractNumberService contractNumberService,
                                     RiskItemMapper riskMapper,
                                     RiskReportMapper riskReportMapper,
                                     ApprovalInstanceMapper approvalMapper,
                                     ApprovalRecordMapper approvalRecordMapper,
                                     FulfillmentPlanMapper planMapper,
                                     AiDraftService aiDraftService,
                                     StatusTransitionService statusTransitionService) {
        this.contractMapper = contractMapper;
        this.attachmentMapper = attachmentMapper;
        this.contractNumberService = contractNumberService;
        this.riskMapper = riskMapper;
        this.riskReportMapper = riskReportMapper;
        this.approvalMapper = approvalMapper;
        this.approvalRecordMapper = approvalRecordMapper;
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

    private void applyRiskReportDataScope(LambdaQueryWrapper<RiskReport> wrapper) {
        String scope = SecurityContext.dataScope();
        Long currentUserId = SecurityContext.userId();
        Long currentDeptId = SecurityContext.deptId();
        if ("SELF".equals(scope) && currentUserId != null) {
            wrapper.and(w -> w.isNull(RiskReport::getContractId)
                    .or().inSql(RiskReport::getContractId,
                            "SELECT contract_id FROM contract_main WHERE owner_id = " + currentUserId));
        } else if ("DEPT".equals(scope) && currentDeptId != null) {
            wrapper.and(w -> w.isNull(RiskReport::getContractId)
                    .or().inSql(RiskReport::getContractId,
                            "SELECT contract_id FROM contract_main WHERE dept_id = " + currentDeptId));
        }
    }

    // ==================== 仪表盘 ====================

    public DashboardVO dashboard() {
        List<ContractMain> contracts = listContracts(null, null, null, null);
        List<FulfillmentPlan> plans = listPlans();
        long approving = countPendingApprovals();
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

    private long countPendingApprovals() {
        return approvalMapper.selectList(new LambdaQueryWrapper<Approval>()
                        .eq(Approval::getStatus, "RUNNING"))
                .stream()
                .filter(approval -> {
                    ContractMain contract = contractMapper.selectById(approval.getContractId());
                    return canAccess(contract);
                })
                .count();
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
        return contractNumberService.withNextNumber(contractNo -> {
            ContractMain contract = new ContractMain();
            contract.setContractNo(contractNo);
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
        });
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
        if (!"DRAFT".equals(contract.getStatus())) {
            throw new IllegalStateException("仅草稿状态的合同可以删除");
        }
        if (contractMapper.deleteById(contractId) != 1) {
            throw new IllegalStateException("草稿删除失败，请刷新后重试");
        }
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

    @Transactional
    public ContractMain submitApproval(Long contractId) {
        ContractMain contract = findContract(contractId);
        if (!"DRAFT".equals(contract.getStatus())) {
            throw new IllegalStateException("仅草稿状态的合同可以提交审批");
        }
        if ("HIGH".equals(contract.getRiskLevel())) {
            throw new IllegalStateException("存在未复核的高风险问题，已阻断提交审批");
        }
        statusTransitionService.transitionToApproving(contract);

        String flowType = determineFlowType(contract.getAmount(), contract.getRiskLevel());
        Approval instance = new Approval();
        instance.setContractId(contractId);
        instance.setFlowType(flowType);
        instance.setCurrentNode("部门主管审批");
        instance.setStatus("RUNNING");
        instance.setStartedAt(LocalDateTime.now());
        instance.setCreatedBy(SecurityContext.username());
        instance.setCreatedAt(LocalDateTime.now());
        instance.setDeleted(0);
        instance.setVersion(1);
        approvalMapper.insert(instance);

        ApprovalRecord submitRecord = new ApprovalRecord();
        submitRecord.setInstanceId(instance.getInstanceId());
        submitRecord.setNodeName("提交审批");
        submitRecord.setApproverId(SecurityContext.userId());
        submitRecord.setAction("SUBMIT");
        submitRecord.setComment("提交审批");
        submitRecord.setActionTime(LocalDateTime.now());
        approvalRecordMapper.insert(submitRecord);
        return contract;
    }

    private String determineFlowType(BigDecimal amount, String riskLevel) {
        if (amount != null && amount.compareTo(superThreshold) >= 0) return "SUPER";
        if (amount != null && amount.compareTo(majorThreshold) >= 0) return "MAJOR";
        if ("MEDIUM".equals(riskLevel)) return "MAJOR";
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

    @Transactional
    public AiRiskReviewResult aiRiskReview(AiRiskReviewRequest request) {
        AiRiskReviewRequest effectiveRequest = ensureRiskReviewContract(request);
        linkReviewAttachment(effectiveRequest);
        List<AiRiskVO> risks = aiDraftService.analyzeRisks(effectiveRequest);
        RiskReport report = persistRiskReport(effectiveRequest, risks);
        return new AiRiskReviewResult(
                report.getReportId(), report.getContractId(), report.getVersionId(),
                report.getReportNo(), report.getHighestRiskLevel(), report.getRiskCount(), risks);
    }

    private AiRiskReviewRequest ensureRiskReviewContract(AiRiskReviewRequest request) {
        if (request.contractId() != null) {
            assertCanAccess(request.contractId());
            return request;
        }
        ContractMain contract = createContract(new ContractCreateRequest(
                defaultReviewTitle(request),
                defaultReviewType(request.contractType()),
                BigDecimal.ZERO,
                defaultReviewCounterparty(request),
                SecurityContext.deptId() != null ? SecurityContext.deptId() : 1L,
                SecurityContext.userId() != null ? SecurityContext.userId() : 1L,
                null,
                LocalDate.now().plusDays(90)
        ));
        return new AiRiskReviewRequest(
                request.contractText(),
                contract.getContractId(),
                request.attachmentId(),
                request.versionId(),
                request.contractType(),
                request.partyA(),
                request.partyB(),
                request.businessScope(),
                request.specialTerms()
        );
    }

    private void linkReviewAttachment(AiRiskReviewRequest request) {
        if (request.attachmentId() == null || request.contractId() == null) {
            return;
        }
        ContractAttachment attachment = attachmentMapper.selectById(request.attachmentId());
        if (attachment == null) {
            throw new IllegalArgumentException("审查附件不存在，请重新上传 PDF");
        }
        if (attachment.getContractId() != null
                && !Objects.equals(attachment.getContractId(), request.contractId())) {
            throw new IllegalStateException("该 PDF 附件已关联到其他合同，请重新选择合同或重新上传");
        }
        if (attachment.getContractId() == null && !"ALL".equals(SecurityContext.dataScope())) {
            Long currentUserId = SecurityContext.userId();
            String expectedCreatedBy = currentUserId == null ? SecurityContext.username() : String.valueOf(currentUserId);
            if (!Objects.equals(attachment.getCreatedBy(), expectedCreatedBy)) {
                throw new SecurityException("无权关联该 PDF 附件");
            }
        }
        if (Objects.equals(attachment.getContractId(), request.contractId())) {
            return;
        }
        attachment.setContractId(request.contractId());
        attachment.setUpdatedBy(SecurityContext.username());
        attachment.setUpdatedAt(LocalDateTime.now());
        attachmentMapper.updateById(attachment);
    }

    private RiskReport persistRiskReport(AiRiskReviewRequest request, List<AiRiskVO> risks) {
        LocalDateTime now = LocalDateTime.now();
        RiskReport report = new RiskReport();
        report.setContractId(request.contractId());
        report.setVersionId(request.versionId());
        report.setReportNo("RISK-" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + Math.floorMod(System.nanoTime(), 10000));
        report.setContractType(clipNullable(request.contractType(), 80));
        report.setPartyA(clipNullable(request.partyA(), 200));
        report.setPartyB(clipNullable(request.partyB(), 200));
        report.setBusinessScope(clipNullable(request.businessScope(), 255));
        report.setHighestRiskLevel(highestRiskLevel(risks));
        report.setRiskCount(risks.size());
        report.setHighCount(countRiskLevel(risks, "HIGH"));
        report.setMediumCount(countRiskLevel(risks, "MEDIUM"));
        report.setLowCount(countRiskLevel(risks, "LOW"));
        report.setContractText(request.contractText());
        report.setSummary(buildReportSummary(report));
        report.setModelName("Qwen");
        report.setReviewStatus("COMPLETED");
        report.setCreatedBy(SecurityContext.username());
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        riskReportMapper.insert(report);
        persistAiRiskItems(report, risks, now);

        if (request.contractId() != null) {
            ContractMain contract = findContract(request.contractId());
            contract.setRiskLevel(report.getHighestRiskLevel());
            contract.setUpdatedBy(SecurityContext.username());
            contract.setUpdatedAt(now);
            contractMapper.updateById(contract);
        }
        return report;
    }

    private void persistAiRiskItems(RiskReport report, List<AiRiskVO> risks, LocalDateTime now) {
        for (AiRiskVO risk : risks) {
            RiskItem item = new RiskItem();
            item.setReportId(report.getReportId());
            item.setContractId(report.getContractId());
            item.setVersionId(report.getVersionId());
            item.setClauseRef(clip(risk.clause(), 255));
            item.setRiskType("AI_REVIEW");
            item.setRiskLevel(normalizeRiskLevel(risk.level()));
            item.setSuggestion(buildRiskSuggestion(risk));
            item.setReviewStatus("AI_PENDING");
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            riskMapper.insert(item);
        }
    }

    public List<RiskReportVO> listRiskReports(Long contractId) {
        if (contractId != null) {
            assertCanAccess(contractId);
        }
        LambdaQueryWrapper<RiskReport> wrapper = new LambdaQueryWrapper<RiskReport>()
                .eq(contractId != null, RiskReport::getContractId, contractId)
                .orderByDesc(RiskReport::getCreatedAt);
        applyRiskReportDataScope(wrapper);
        return riskReportMapper.selectList(wrapper).stream()
                .map(report -> toRiskReportVo(report, false))
                .toList();
    }

    public RiskReportVO getRiskReport(Long reportId) {
        RiskReport report = riskReportMapper.selectById(reportId);
        if (report == null) {
            throw new IllegalArgumentException("风险报告不存在");
        }
        if (report.getContractId() != null) {
            assertCanAccess(report.getContractId());
        }
        return toRiskReportVo(report, true);
    }

    private RiskReportVO toRiskReportVo(RiskReport report, boolean includeDetails) {
        List<AiRiskVO> risks = includeDetails
                ? riskMapper.selectList(new LambdaQueryWrapper<RiskItem>()
                    .eq(RiskItem::getReportId, report.getReportId())
                    .orderByAsc(RiskItem::getRiskId))
                    .stream()
                    .map(this::toAiRiskVo)
                    .toList()
                : List.of();
        return new RiskReportVO(
                report.getReportId(), report.getContractId(), report.getVersionId(),
                report.getReportNo(), report.getContractType(), report.getPartyA(), report.getPartyB(),
                report.getBusinessScope(), report.getHighestRiskLevel(), report.getRiskCount(),
                report.getHighCount(), report.getMediumCount(), report.getLowCount(),
                includeDetails ? report.getContractText() : null, report.getSummary(), report.getModelName(),
                report.getReviewStatus(), report.getCreatedBy(), report.getCreatedAt(), risks);
    }

    private AiRiskVO toAiRiskVo(RiskItem item) {
        String stored = Objects.toString(item.getSuggestion(), "");
        String reason = "";
        String suggestion = stored;
        int separator = stored.indexOf('\n');
        if (separator >= 0) {
            reason = stored.substring(0, separator).replaceFirst("^风险原因：", "").trim();
            suggestion = stored.substring(separator + 1).replaceFirst("^修改建议：", "").trim();
        }
        return new AiRiskVO(item.getRiskLevel(), item.getClauseRef(), reason, suggestion);
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

    private String defaultReviewTitle(AiRiskReviewRequest request) {
        String type = StringUtils.hasText(request.contractType()) ? request.contractType().trim() : "PDF合同";
        return clip(type + "风险审查", 120);
    }

    private String defaultReviewType(String contractType) {
        if (!StringUtils.hasText(contractType)) return "TECH";
        if (contractType.contains("采购")) return "PURCHASE";
        if (contractType.contains("销售")) return "SALES";
        if (contractType.contains("劳务")) return "LABOR";
        if (contractType.contains("技术")) return "TECH";
        return "TECH";
    }

    private String defaultReviewCounterparty(AiRiskReviewRequest request) {
        if (StringUtils.hasText(request.partyB())) return clip(request.partyB(), 120);
        if (StringUtils.hasText(request.partyA())) return clip(request.partyA(), 120);
        return "PDF导入合同";
    }

    private String highestRiskLevel(List<AiRiskVO> risks) {
        if (risks.stream().anyMatch(risk -> "HIGH".equals(normalizeRiskLevel(risk.level())))) return "HIGH";
        if (risks.stream().anyMatch(risk -> "MEDIUM".equals(normalizeRiskLevel(risk.level())))) return "MEDIUM";
        return "LOW";
    }

    private int countRiskLevel(List<AiRiskVO> risks, String level) {
        return (int) risks.stream()
                .filter(risk -> level.equals(normalizeRiskLevel(risk.level())))
                .count();
    }

    private String normalizeRiskLevel(String level) {
        String normalized = Objects.toString(level, "LOW").trim().toUpperCase();
        return Set.of("HIGH", "MEDIUM", "LOW").contains(normalized) ? normalized : "LOW";
    }

    private String buildReportSummary(RiskReport report) {
        if (report.getRiskCount() == null || report.getRiskCount() == 0) {
            return "风险审查完成，未发现明显风险。";
        }
        return "风险审查完成，共发现 " + report.getRiskCount() + " 项风险，其中高风险 "
                + report.getHighCount() + " 项、中风险 " + report.getMediumCount()
                + " 项、低风险 " + report.getLowCount() + " 项。";
    }

    private String buildRiskSuggestion(AiRiskVO risk) {
        String reason = StringUtils.hasText(risk.reason()) ? risk.reason().trim() : "AI 未返回详细原因";
        String suggestion = StringUtils.hasText(risk.suggestion()) ? risk.suggestion().trim() : "请法务复核该风险项";
        return "风险原因：" + reason + "\n修改建议：" + suggestion;
    }

    private String clip(String value, int max) {
        if (!StringUtils.hasText(value)) return "AI";
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private String clipNullable(String value, int max) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
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
