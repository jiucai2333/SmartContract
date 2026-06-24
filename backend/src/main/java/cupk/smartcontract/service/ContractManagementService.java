package cupk.smartcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cupk.smartcontract.security.SecurityContext;
import cupk.smartcontract.entity.Approval;
import cupk.smartcontract.entity.ApprovalRecord;
import cupk.smartcontract.entity.ArchiveRecord;
import cupk.smartcontract.entity.ContractMain;
import cupk.smartcontract.entity.ContractAttachment;
import cupk.smartcontract.entity.ContractVersion;
import cupk.smartcontract.entity.ContractTemplate;
import cupk.smartcontract.entity.FileInfo;
import cupk.smartcontract.entity.FulfillmentPlan;
import cupk.smartcontract.entity.RiskItem;
import cupk.smartcontract.entity.RiskReport;
import cupk.smartcontract.entity.SealRecord;
import cupk.smartcontract.dto.ArchiveCreateRequest;
import cupk.smartcontract.dto.AiRiskReviewRequest;
import cupk.smartcontract.dto.AiRiskReviewResult;
import cupk.smartcontract.dto.AiRiskVO;
import cupk.smartcontract.dto.ContractCreateRequest;
import cupk.smartcontract.dto.DashboardVO;
import cupk.smartcontract.dto.RiskReportVO;
import cupk.smartcontract.dto.SealCreateRequest;
import cupk.smartcontract.mapper.ApprovalInstanceMapper;
import org.slf4j.LoggerFactory;
import cupk.smartcontract.mapper.ApprovalRecordMapper;
import cupk.smartcontract.mapper.ContractMainMapper;
import cupk.smartcontract.mapper.ContractAttachmentMapper;
import cupk.smartcontract.mapper.ContractAttachmentOcrMapper;
import cupk.smartcontract.mapper.ContractVersionMapper;
import cupk.smartcontract.mapper.ContractTemplateMapper;
import cupk.smartcontract.mapper.FileInfoMapper;
import cupk.smartcontract.mapper.FulfillmentPlanMapper;
import cupk.smartcontract.mapper.RiskItemMapper;
import cupk.smartcontract.mapper.RiskReportMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ContractManagementService {

    @Value("${contract.threshold.major:100000}")
    private BigDecimal majorThreshold;

    @Value("${contract.threshold.super:500000}")
    private BigDecimal superThreshold;

    private final ContractMainMapper contractMapper;
    private final ContractNumberService contractNumberService;
    private final RiskItemMapper riskMapper;
    private final RiskReportMapper riskReportMapper;
    private final ApprovalInstanceMapper approvalMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final FulfillmentPlanMapper planMapper;
    private final AiDraftService aiDraftService;
    private final StatusTransitionService statusTransitionService;
    private final ContractVersionMapper contractVersionMapper;
    private final ContractAttachmentMapper contractAttachmentMapper;
    private final ContractAttachmentOcrMapper contractAttachmentOcrMapper;
    private final ContractTemplateMapper contractTemplateMapper;
    private final FileInfoMapper fileInfoMapper;
    private final FileStorageService fileStorageService;
    private final ContractFieldAnalysisService contractFieldAnalysisService;
    private final BlockchainService blockchainService;
    private final WordArchiveService wordArchiveService;

    public ContractManagementService(ContractMainMapper contractMapper,
                                     ContractNumberService contractNumberService,
                                     RiskItemMapper riskMapper,
                                     RiskReportMapper riskReportMapper,
                                     ApprovalInstanceMapper approvalMapper,
                                     ApprovalRecordMapper approvalRecordMapper,
                                     FulfillmentPlanMapper planMapper,
                                     AiDraftService aiDraftService,
                                     StatusTransitionService statusTransitionService,
                                     ContractVersionMapper contractVersionMapper,
                                     ContractAttachmentMapper contractAttachmentMapper,
                                     ContractAttachmentOcrMapper contractAttachmentOcrMapper,
                                     ContractTemplateMapper contractTemplateMapper,
                                     FileInfoMapper fileInfoMapper,
                                     FileStorageService fileStorageService,
                                     ContractFieldAnalysisService contractFieldAnalysisService,
                                     BlockchainService blockchainService,
                                     WordArchiveService wordArchiveService) {
        this.contractMapper = contractMapper;
        this.contractNumberService = contractNumberService;
        this.riskMapper = riskMapper;
        this.riskReportMapper = riskReportMapper;
        this.approvalMapper = approvalMapper;
        this.approvalRecordMapper = approvalRecordMapper;
        this.planMapper = planMapper;
        this.aiDraftService = aiDraftService;
        this.statusTransitionService = statusTransitionService;
        this.contractVersionMapper = contractVersionMapper;
        this.contractAttachmentMapper = contractAttachmentMapper;
        this.contractAttachmentOcrMapper = contractAttachmentOcrMapper;
        this.contractTemplateMapper = contractTemplateMapper;
        this.fileInfoMapper = fileInfoMapper;
        this.fileStorageService = fileStorageService;
        this.contractFieldAnalysisService = contractFieldAnalysisService;
        this.blockchainService = blockchainService;
        this.wordArchiveService = wordArchiveService;
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
        return listContracts(keyword, status, riskLevel, type, null, null, null, null);
    }

    public List<ContractMain> listContracts(String keyword, String status, String riskLevel, String type,
                                            LocalDate signDateFrom, LocalDate signDateTo,
                                            BigDecimal amountMin, BigDecimal amountMax) {
        LambdaQueryWrapper<ContractMain> wrapper = new LambdaQueryWrapper<ContractMain>()
                .and(StringUtils.hasText(keyword), w -> w.like(ContractMain::getTitle, keyword)
                        .or().like(ContractMain::getCounterparty, keyword)
                        .or().like(ContractMain::getContractNo, keyword))
                .eq(StringUtils.hasText(status), ContractMain::getStatus, status)
                .eq(StringUtils.hasText(riskLevel), ContractMain::getRiskLevel, riskLevel)
                .eq(StringUtils.hasText(type), ContractMain::getType, type)
                .ge(amountMin != null, ContractMain::getAmount, amountMin)
                .le(amountMax != null, ContractMain::getAmount, amountMax)
                .orderByDesc(ContractMain::getCreatedAt);
        applyDataScope(wrapper);
        List<ContractMain> contracts = contractMapper.selectList(wrapper);
        if (signDateFrom != null || signDateTo != null) {
            contracts = contracts.stream()
                    .filter(c -> {
                        LocalDateTime createdAt = c.getCreatedAt();
                        if (createdAt == null) return true;
                        LocalDate createdDate = createdAt.toLocalDate();
                        boolean afterStart = signDateFrom == null || !createdDate.isBefore(signDateFrom);
                        boolean beforeEnd = signDateTo == null || !createdDate.isAfter(signDateTo);
                        return afterStart && beforeEnd;
                    })
                    .toList();
        }
        attachLatestVersions(contracts);
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
            contract.setTemplateId(request.templateId());
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

    @Transactional(rollbackFor = Exception.class)
    public void deleteContract(Long contractId) {
        ContractMain contract = findContract(contractId);
        assertCanAccess(contractId);
        if (!"DRAFT".equals(contract.getStatus())) {
            throw new IllegalStateException("仅草稿状态的合同可以删除");
        }
        List<ContractVersion> versions = contractVersionMapper.selectList(
                new LambdaQueryWrapper<ContractVersion>().eq(ContractVersion::getContractId, contractId));
        List<ContractAttachment> attachments = contractAttachmentMapper.selectList(
                new LambdaQueryWrapper<ContractAttachment>().eq(ContractAttachment::getContractId, contractId));
        Set<Long> candidateFileIds = new LinkedHashSet<>();
        versions.stream().map(ContractVersion::getFileId).filter(Objects::nonNull).forEach(candidateFileIds::add);
        attachments.stream().map(ContractAttachment::getFileId).filter(Objects::nonNull).forEach(candidateFileIds::add);

        String updatedBy = SecurityContext.username();
        for (ContractAttachment attachment : attachments) {
            contractAttachmentOcrMapper.logicDeleteByAttachmentId(attachment.getAttachmentId(), updatedBy);
            contractAttachmentMapper.deleteById(attachment.getAttachmentId());
        }
        for (ContractVersion version : versions) {
            contractVersionMapper.deleteById(version.getVersionId());
        }
        if (contractMapper.deleteById(contractId) != 1) {
            throw new IllegalStateException("草稿删除失败，请刷新后重试");
        }
        registerFileCleanup(candidateFileIds);
    }

    public void lockContract(Long contractId) {
        if (contractMapper.lockById(contractId) == null) {
            throw new IllegalArgumentException("Contract does not exist");
        }
    }

    private void attachLatestVersions(List<ContractMain> contracts) {
        if (contracts.isEmpty()) return;
        List<Long> contractIds = contracts.stream().map(ContractMain::getContractId).toList();
        List<ContractVersion> versions = contractVersionMapper.selectList(
                new LambdaQueryWrapper<ContractVersion>()
                        .in(ContractVersion::getContractId, contractIds)
                        .orderByDesc(ContractVersion::getCreatedAt)
                        .orderByDesc(ContractVersion::getVersionId));
        Map<Long, ContractVersion> latestByContract = new LinkedHashMap<>();
        for (ContractVersion version : versions) {
            latestByContract.putIfAbsent(version.getContractId(), version);
        }
        for (ContractMain contract : contracts) {
            ContractVersion version = latestByContract.get(contract.getContractId());
            if (version == null) continue;
            contract.setLatestVersionId(version.getVersionId());
            contract.setLatestVersionNo(version.getVersionNo());
            if (version.getFileId() != null) {
                contract.setLatestDownloadUrl("/api/contracts/" + contract.getContractId()
                        + "/versions/" + version.getVersionId() + "/download");
            }
        }
    }

    private void registerFileCleanup(Set<Long> candidateFileIds) {
        if (candidateFileIds.isEmpty()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                candidateFileIds.forEach(ContractManagementService.this::cleanupUnreferencedFile);
            }
        });
    }

    private void cleanupUnreferencedFile(Long fileId) {
        try {
            if (hasFileReferences(fileId)) return;
            FileInfo file = fileInfoMapper.selectById(fileId);
            if (file == null) return;
            fileInfoMapper.deleteById(fileId);
            fileStorageService.delete(file.getObjectKey());
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(ContractManagementService.class)
                    .error("Failed to clean unreferenced file {}", fileId, ex);
        }
    }

    private boolean hasFileReferences(Long fileId) {
        Long templateCount = contractTemplateMapper.selectCount(
                new LambdaQueryWrapper<ContractTemplate>().eq(ContractTemplate::getFileId, fileId));
        Long versionCount = contractVersionMapper.selectCount(
                new LambdaQueryWrapper<ContractVersion>().eq(ContractVersion::getFileId, fileId));
        Long attachmentCount = contractAttachmentMapper.selectCount(
                new LambdaQueryWrapper<ContractAttachment>().eq(ContractAttachment::getFileId, fileId));
        return count(templateCount) + count(versionCount) + count(attachmentCount) > 0;
    }

    private long count(Long value) {
        return value == null ? 0 : value;
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
        AiRiskReviewRequest effectiveRequest = ensureRiskReviewContract(normalizeRiskReviewRequest(request));
        linkReviewAttachment(effectiveRequest);
        List<AiRiskVO> risks = enrichRiskClauses(
                aiDraftService.analyzeRisks(effectiveRequest),
                effectiveRequest.contractText());
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
                null,
                LocalDate.now().plusDays(90)
        ));
        return new AiRiskReviewRequest(
                normalizeEscapedLineBreaks(request.contractText()),
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

    private AiRiskReviewRequest normalizeRiskReviewRequest(AiRiskReviewRequest request) {
        return new AiRiskReviewRequest(
                normalizeEscapedLineBreaks(request.contractText()),
                request.contractId(),
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
        ContractAttachment attachment = contractAttachmentMapper.selectById(request.attachmentId());
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
        contractAttachmentMapper.updateById(attachment);
    }

    private RiskReport persistRiskReport(AiRiskReviewRequest request, List<AiRiskVO> risks) {
        LocalDateTime now = LocalDateTime.now();
        RiskReport report = new RiskReport();
        report.setContractId(request.contractId());
        report.setVersionId(resolveReviewVersionId(request));
        report.setReportNo(nextRiskReportNo(request.contractId(), now));
        report.setContractType(clipNullable(request.contractType(), 80));
        report.setPartyA(clipNullable(request.partyA(), 200));
        report.setPartyB(clipNullable(request.partyB(), 200));
        report.setBusinessScope(clipNullable(request.businessScope(), 255));
        report.setHighestRiskLevel(highestRiskLevel(risks));
        report.setRiskCount(risks.size());
        report.setHighCount(countRiskLevel(risks, "HIGH"));
        report.setMediumCount(countRiskLevel(risks, "MEDIUM"));
        report.setLowCount(countRiskLevel(risks, "LOW"));
        report.setContractText(normalizeEscapedLineBreaks(request.contractText()));
        report.setSummary(buildReportSummary(report));
        report.setModelName("Qwen");
        report.setReviewStatus("COMPLETED");
        report.setCreatedBy(SecurityContext.username());
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        riskReportMapper.insert(report);
        persistRiskReportAttachment(report, risks, now);
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

    private List<AiRiskVO> enrichRiskClauses(List<AiRiskVO> risks, String contractText) {
        if (risks == null || risks.isEmpty() || !StringUtils.hasText(contractText)) {
            return risks == null ? List.of() : risks;
        }
        return risks.stream()
                .map(risk -> {
                    String clause = risk.clause();
                    if (isUsableRiskClause(clause, contractText)) {
                        return risk;
                    }
                    String resolved = resolveRiskClauseFromText(risk, contractText);
                    return StringUtils.hasText(resolved)
                            ? new AiRiskVO(risk.level(), risk.category(), resolved, risk.reason(), risk.suggestion(), risk.replacement())
                            : risk;
                })
                .toList();
    }

    private boolean isUsableRiskClause(String clause, String contractText) {
        if (!StringUtils.hasText(clause) || clause.trim().length() < 12) return false;
        return normalizeForRiskMatch(contractText).contains(normalizeForRiskMatch(clause));
    }

    private String resolveRiskClauseFromText(AiRiskVO risk, String contractText) {
        String article = extractArticleRef(risk.clause());
        List<String> keywords = riskKeywords(risk);
        String best = "";
        int bestScore = 0;
        String[] blocks = contractText.replace("\r", "").split("\\n+");
        for (String raw : blocks) {
            String block = raw == null ? "" : raw.trim();
            String compact = normalizeForRiskMatch(block);
            if (compact.length() < 12) continue;
            int score = 0;
            if (StringUtils.hasText(article) && compact.contains(normalizeForRiskMatch(article))) {
                score += 8;
            }
            for (String keyword : keywords) {
                if (compact.contains(normalizeForRiskMatch(keyword))) {
                    score += keyword.length() >= 4 ? 3 : 2;
                }
            }
            if (score > bestScore || score == bestScore && block.length() > best.length()) {
                bestScore = score;
                best = block;
            }
        }
        return bestScore >= 4 ? clip(best, 240) : "";
    }

    private String extractArticleRef(String value) {
        if (!StringUtils.hasText(value)) return "";
        Matcher matcher = Pattern.compile("第[一二三四五六七八九十百千万0-9]+条").matcher(value);
        return matcher.find() ? matcher.group() : "";
    }

    private List<String> riskKeywords(AiRiskVO risk) {
        String category = normalizeRiskCategory(risk.category(), risk.clause(), risk.reason(), risk.suggestion());
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        switch (category) {
            case "SUBJECT_INFO" -> keywords.addAll(List.of("甲方", "乙方", "主体", "名称", "统一社会信用代码", "授权代表", "地址", "联系人", "联系电话"));
            case "PAYMENT" -> keywords.addAll(List.of("付款", "支付", "金额", "价款", "发票", "结算", "款项", "账户"));
            case "TERM" -> keywords.addAll(List.of("期限", "日期", "交付", "验收", "生效", "到期", "解除"));
            case "DISPUTE_RESOLUTION" -> keywords.addAll(List.of("争议", "管辖", "仲裁", "法院", "诉讼", "适用法律"));
            default -> keywords.addAll(List.of("违约", "赔偿", "责任", "违约金", "免责", "损失"));
        }
        for (String value : new String[]{risk.clause(), risk.reason(), risk.suggestion()}) {
            if (!StringUtils.hasText(value)) continue;
            Matcher matcher = Pattern.compile("[\\p{IsHan}A-Za-z0-9]{2,12}").matcher(value);
            while (matcher.find() && keywords.size() < 18) {
                String word = matcher.group();
                if (!Set.of("风险", "原因", "建议", "修改", "合同", "条款", "明确", "具体").contains(word)) {
                    keywords.add(word);
                }
            }
        }
        return keywords.stream().toList();
    }

    private String normalizeForRiskMatch(String value) {
        return Objects.toString(value, "")
                .replace('\u00a0', ' ')
                .replaceAll("[\\s　]+", "")
                .replace('：', ':')
                .trim();
    }

    private Long resolveReviewVersionId(AiRiskReviewRequest request) {
        if (request.versionId() != null) {
            return request.versionId();
        }
        if (request.contractId() == null) {
            return null;
        }
        ContractVersion latest = contractVersionMapper.selectOne(
                new LambdaQueryWrapper<ContractVersion>()
                        .eq(ContractVersion::getContractId, request.contractId())
                        .orderByDesc(ContractVersion::getCreatedAt)
                        .last("LIMIT 1"));
        return latest == null ? null : latest.getVersionId();
    }

    private String nextRiskReportNo(Long contractId, LocalDateTime now) {
        LambdaQueryWrapper<RiskReport> wrapper = new LambdaQueryWrapper<>();
        if (contractId == null) {
            wrapper.isNull(RiskReport::getContractId);
        } else {
            wrapper.eq(RiskReport::getContractId, contractId);
        }
        Long existing = riskReportMapper.selectCount(wrapper);
        long next = (existing == null ? 0 : existing) + 1;
        return "RISK-V" + next + "-" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private void persistRiskReportAttachment(RiskReport report, List<AiRiskVO> risks, LocalDateTime now) {
        if (report.getContractId() == null || report.getReportId() == null) {
            return;
        }
        String reportHtml = buildRiskReportAttachmentHtml(report, risks);
        String filename = "风险报告-" + safeAttachmentName(report.getReportNo()) + ".docx";
        try {
            FileStorageService.StoredFile stored = fileStorageService.store(
                    wordArchiveService.toDocx(reportHtml), filename, "docx");
            String operator = SecurityContext.username();
            FileInfo file = createStoredFile(stored, filename, operator, now);

            ContractAttachment attachment = new ContractAttachment();
            attachment.setContractId(report.getContractId());
            attachment.setFileId(file.getFileId());
            attachment.setAttachType("RISK_REPORT");
            attachment.setRemark("AI风险审查报告：" + report.getReportNo());
            attachment.setCreatedBy(operator);
            attachment.setUpdatedBy(operator);
            attachment.setCreatedAt(now);
            attachment.setUpdatedAt(now);
            attachment.setDeleted(0);
            attachment.setVersion(1);
            contractAttachmentMapper.insert(attachment);
        } catch (IOException ex) {
            throw new IllegalStateException("风险报告附件生成失败：" + ex.getMessage(), ex);
        }
    }

    private ContractVersion persistRiskAnnotatedDraft(RiskReport report, List<AiRiskVO> risks, LocalDateTime now) {
        if (report.getContractId() == null || report.getReportId() == null) {
            return null;
        }
        String annotatedHtml = buildAnnotatedContractHtml(report, risks);
        String filename = "风险标注合同-" + safeAttachmentName(report.getReportNo()) + ".docx";
        try {
            FileStorageService.StoredFile stored = fileStorageService.store(
                    wordArchiveService.toDocx(annotatedHtml), filename, "docx");
            String operator = SecurityContext.username();
            FileInfo file = createStoredFile(stored, filename, operator, now);

            removeExistingRiskAttachments(report.getContractId(), now);

            ContractVersion version = new ContractVersion();
            version.setContractId(report.getContractId());
            version.setVersionNo(nextVersionNo(report.getContractId()));
            version.setContentHash(sha256(annotatedHtml));
            version.setFileId(file.getFileId());
            version.setContent(annotatedHtml);
            version.setCreatedBy(operator);
            version.setUpdatedBy(operator);
            version.setCreatedAt(now);
            version.setUpdatedAt(now);
            version.setIsDeleted(0);
            version.setIsLocked(false);
            version.setVersion(1);
            contractVersionMapper.insert(version);

            ContractAttachment attachment = new ContractAttachment();
            attachment.setContractId(report.getContractId());
            attachment.setFileId(file.getFileId());
            attachment.setAttachType("RISK_REPORT");
            attachment.setRemark("AI风险标注合同：" + report.getReportNo());
            attachment.setCreatedBy(operator);
            attachment.setUpdatedBy(operator);
            attachment.setCreatedAt(now);
            attachment.setUpdatedAt(now);
            attachment.setDeleted(0);
            attachment.setVersion(1);
            contractAttachmentMapper.insert(attachment);
            return version;
        } catch (IOException ex) {
            throw new IllegalStateException("风险标注合同生成失败：" + ex.getMessage(), ex);
        }
    }

    private void removeExistingRiskAttachments(Long contractId, LocalDateTime now) {
        List<ContractAttachment> existing = contractAttachmentMapper.selectList(
                new LambdaQueryWrapper<ContractAttachment>()
                        .eq(ContractAttachment::getContractId, contractId)
                        .eq(ContractAttachment::getAttachType, "RISK_REPORT"));
        for (ContractAttachment attachment : existing) {
            attachment.setDeleted(1);
            attachment.setUpdatedBy(SecurityContext.username());
            attachment.setUpdatedAt(now);
            contractAttachmentMapper.updateById(attachment);
        }
    }

    private FileInfo createStoredFile(FileStorageService.StoredFile stored, String filename,
                                      String operator, LocalDateTime now) {
        FileInfo file = new FileInfo();
        file.setObjectKey(stored.objectKey());
        file.setFileName(filename);
        file.setFileType(stored.fileType());
        file.setSize(stored.size());
        file.setSha256(stored.sha256());
        file.setCreatedBy(operator);
        file.setUpdatedBy(operator);
        file.setCreatedAt(now);
        file.setUpdatedAt(now);
        file.setDeleted(0);
        file.setVersion(1);
        fileInfoMapper.insert(file);
        return file;
    }

    private String buildRiskReportAttachmentHtml(RiskReport report, List<AiRiskVO> risks) {
        StringBuilder html = new StringBuilder();
        html.append("<h1>合同风险审查报告</h1>");
        html.append("<h2>一、报告概览</h2><table class=\"risk-overview-table\">");
        htmlRow(html, "报告编号", report.getReportNo());
        htmlRow(html, "关联草稿版本", report.getVersionId() == null ? "" : "#" + report.getVersionId());
        htmlRow(html, "合同类型", report.getContractType());
        htmlRow(html, "甲方", report.getPartyA());
        htmlRow(html, "乙方", report.getPartyB());
        htmlRow(html, "业务范围", report.getBusinessScope());
        htmlRow(html, "最高风险等级", readableRiskLevel(report.getHighestRiskLevel()));
        htmlRow(html, "风险总数", report.getRiskCount());
        htmlRow(html, "高 / 中 / 低风险",
                countValue(report.getHighCount()) + " / " + countValue(report.getMediumCount())
                        + " / " + countValue(report.getLowCount()));
        htmlRow(html, "摘要", report.getSummary());
        htmlRow(html, "模型", report.getModelName());
        htmlRow(html, "审查人", report.getCreatedBy());
        htmlRow(html, "生成时间", report.getCreatedAt() == null
                ? "" : report.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        html.append("</table>");
        html.append("<h2>二、风险修改建议</h2>");
        if (risks == null || risks.isEmpty()) {
            html.append("<p>本次审查未发现明显风险。</p>");
            return html.toString();
        }
        int index = 1;
        for (AiRiskVO risk : risks) {
            html.append("<h3>").append(index++).append(". ")
                    .append(escapeHtml(readableRiskLevel(risk.level()))).append(" - ")
                    .append(escapeHtml(readableRiskCategory(risk.category()))).append("</h3>")
                    .append("<p><strong>风险原文：</strong>").append(escapeHtml(risk.clause())).append("</p>")
                    .append("<p><strong>风险原因：</strong>").append(escapeHtml(risk.reason())).append("</p>")
                    .append("<p><strong>修改建议：</strong>").append(escapeHtml(risk.suggestion())).append("</p>");
            if (StringUtils.hasText(risk.replacement())) {
                html.append("<p><strong>修改后条款：</strong>").append(escapeHtml(risk.replacement())).append("</p>");
            }
        }
        return html.toString();
    }

    private String buildAnnotatedContractHtml(RiskReport report, List<AiRiskVO> risks) {
        StringBuilder html = new StringBuilder();
        html.append("<h1>AI风险标注合同草稿</h1>");
        html.append("<h2>一、合同风险概览</h2><table class=\"risk-overview-table\">");
        htmlRow(html, "报告编号", report.getReportNo());
        htmlRow(html, "合同类型", report.getContractType());
        htmlRow(html, "甲方", report.getPartyA());
        htmlRow(html, "乙方", report.getPartyB());
        htmlRow(html, "业务范围", report.getBusinessScope());
        htmlRow(html, "最高风险等级", readableRiskLevel(report.getHighestRiskLevel()));
        htmlRow(html, "风险总数", report.getRiskCount());
        htmlRow(html, "高 / 中 / 低风险",
                countValue(report.getHighCount()) + " / " + countValue(report.getMediumCount())
                        + " / " + countValue(report.getLowCount()));
        htmlRow(html, "摘要", report.getSummary());
        htmlRow(html, "模型", report.getModelName());
        htmlRow(html, "审查人", report.getCreatedBy());
        htmlRow(html, "生成时间", report.getCreatedAt() == null
                ? "" : report.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        html.append("</table>");
        html.append("<h2>二、合同正文</h2>");
        appendContractTextWithRiskMarks(html, report, risks);
        html.append("<h2>三、风险修改建议</h2>");
        if (risks == null || risks.isEmpty()) {
            html.append("<p>本次审查未发现明显风险，可继续推进合同流程。</p>");
            return html.toString();
        }
        int index = 1;
        for (AiRiskVO risk : risks) {
            String level = normalizeRiskLevel(risk.level());
            html.append("<h3>").append(index++).append(". ")
                    .append(escapeHtml(readableRiskLevel(risk.level()))).append(" - ")
                    .append(escapeHtml(readableRiskCategory(risk.category()))).append(" - ")
                    .append(escapeHtml(risk.clause())).append("</h3>")
                    .append("<p><span style=\"").append(riskUnderlineStyle(level)).append("\">")
                    .append("需修改内容：").append(escapeHtml(risk.clause())).append("</span></p>")
                    .append("<p>风险原因：").append(escapeHtml(risk.reason())).append("</p>")
                    .append("<p>修改建议：").append(escapeHtml(risk.suggestion())).append("</p>")
                    .append("<p><span style=\"").append(suggestionUnderlineStyle()).append("\">")
                    .append("修改后条款：").append(escapeHtml(StringUtils.hasText(risk.replacement()) ? risk.replacement() : risk.suggestion())).append("</span></p>");
        }
        return html.toString();
    }

    private void appendContractTextWithRiskMarks(StringBuilder html, RiskReport report, List<AiRiskVO> risks) {
        String text = Objects.toString(report.getContractText(), "");
        if (!StringUtils.hasText(text)) {
            html.append("<p>暂无可标注的合同正文，请检查PDF解析结果。</p>");
            return;
        }
        String[] paragraphs = normalizeEscapedLineBreaks(text).split("\\n+");
        for (String paragraph : paragraphs) {
            if (!StringUtils.hasText(paragraph)) {
                continue;
            }
            html.append("<p>");
            appendMarkedParagraph(html, paragraph.trim(), risks);
            html.append("</p>");
        }
    }

    private void appendMarkedParagraph(StringBuilder html, String paragraph, List<AiRiskVO> risks) {
        AiRiskVO matchedRisk = null;
        for (AiRiskVO risk : risks == null ? List.<AiRiskVO>of() : risks) {
            if (StringUtils.hasText(risk.clause()) && paragraph.contains(risk.clause().trim())) {
                matchedRisk = risk;
                break;
            }
        }
        if (matchedRisk == null) {
            html.append(escapeHtml(paragraph));
            return;
        }
        String clause = matchedRisk.clause().trim();
        int start = paragraph.indexOf(clause);
        html.append(escapeHtml(paragraph.substring(0, start)));
        html.append("<span style=\"").append(riskUnderlineStyle(normalizeRiskLevel(matchedRisk.level()))).append("\">")
                .append(escapeHtml(clause)).append("</span>");
        html.append(escapeHtml(paragraph.substring(start + clause.length())));
        String replacement = StringUtils.hasText(matchedRisk.replacement())
                ? matchedRisk.replacement().trim() : matchedRisk.suggestion();
        if (StringUtils.hasText(replacement)) {
            html.append("<br/><span style=\"").append(suggestionUnderlineStyle()).append("\">")
                    .append("修改后条款：").append(escapeHtml(replacement))
                    .append("</span>");
        }
    }

    private void persistAiRiskItems(RiskReport report, List<AiRiskVO> risks, LocalDateTime now) {
        for (AiRiskVO risk : risks) {
            RiskItem item = new RiskItem();
            item.setReportId(report.getReportId());
            item.setContractId(report.getContractId());
            item.setVersionId(report.getVersionId());
            item.setClauseRef(clip(risk.clause(), 255));
            item.setRiskType(normalizeRiskCategory(risk.category(), risk.clause(), risk.reason(), risk.suggestion()));
            item.setRiskLevel(normalizeRiskLevel(risk.level()));
            item.setSuggestion(risk.suggestion());
            item.setReplacement(risk.replacement());
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
        String suggestion = Objects.toString(item.getSuggestion(), "");
        String reason = "";
        // 兼容旧格式："风险原因：...\n修改建议：..." → 拆分为 reason + suggestion
        int separator = suggestion.indexOf('\n');
        if (separator >= 0 && !suggestion.startsWith("{")) {
            reason = suggestion.substring(0, separator).replaceFirst("^风险原因：", "").trim();
            suggestion = suggestion.substring(separator + 1).replaceFirst("^修改建议：", "").trim();
        }
        String replacement = item.getReplacement();
        // 旧数据没有 replacement，降级用 suggestion
        if (!StringUtils.hasText(replacement)) {
            replacement = suggestion;
        }
        String category = normalizeRiskCategory(item.getRiskType(), item.getClauseRef(), reason, suggestion);
        return new AiRiskVO(item.getRiskLevel(), category, item.getClauseRef(), reason, suggestion, replacement);
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

    // ==================== 签章与归档 ====================

    public SealRecord seal(SealCreateRequest request) {
        SealRecord record = statusTransitionService.seal(request);
        try {
            blockchainService.anchorToChain(request.contractId(), request.versionId(), "SEAL", "签章登记锚定");
        } catch (Exception e) {
            LoggerFactory.getLogger(ContractManagementService.class)
                    .warn("签章后区块链锚定失败: contractId={}", request.contractId(), e);
        }
        return record;
    }

    public ArchiveRecord archive(ArchiveCreateRequest request) {
        ArchiveRecord record = statusTransitionService.archive(request);
        try {
            blockchainService.anchorToChain(request.contractId(), request.versionId(), "ARCHIVE", "归档确认锚定");
        } catch (Exception e) {
            LoggerFactory.getLogger(ContractManagementService.class)
                    .warn("归档后区块链锚定失败: contractId={}", request.contractId(), e);
        }
        return record;
    }
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

    private String normalizeRiskCategory(String category, String clause, String reason, String suggestion) {
        String normalized = Objects.toString(category, "").trim().toUpperCase();
        if (Set.of("SUBJECT_INFO", "PAYMENT", "LIABILITY", "TERM", "DISPUTE_RESOLUTION").contains(normalized)) {
            return normalized;
        }
        return inferRiskCategory(clause, reason, suggestion);
    }

    private String inferRiskCategory(String... values) {
        String text = java.util.Arrays.stream(values)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" "));
        if (text.contains("甲方") || text.contains("乙方") || text.contains("主体")
                || text.contains("统一社会信用代码") || text.contains("授权") || text.contains("地址")) {
            return "SUBJECT_INFO";
        }
        if (text.contains("付款") || text.contains("支付") || text.contains("金额")
                || text.contains("发票") || text.contains("结算") || text.contains("款项")) {
            return "PAYMENT";
        }
        if (text.contains("违约") || text.contains("赔偿") || text.contains("免责")
                || text.contains("责任") || text.contains("违约金")) {
            return "LIABILITY";
        }
        if (text.contains("期限") || text.contains("日期") || text.contains("交付")
                || text.contains("验收") || text.contains("续期") || text.contains("解除")) {
            return "TERM";
        }
        if (text.contains("争议") || text.contains("管辖") || text.contains("仲裁")
                || text.contains("诉讼") || text.contains("法院") || text.contains("适用法律")) {
            return "DISPUTE_RESOLUTION";
        }
        return "LIABILITY";
    }

    private String buildReportSummary(RiskReport report) {
        if (report.getRiskCount() == null || report.getRiskCount() == 0) {
            return "风险审查完成，未发现明显风险。";
        }
        return "风险审查完成，共发现 " + report.getRiskCount() + " 项风险，其中高风险 "
                + report.getHighCount() + " 项、中风险 " + report.getMediumCount()
                + " 项、低风险 " + report.getLowCount() + " 项。";
    }


    private String readableRiskLevel(String level) {
        return switch (normalizeRiskLevel(level)) {
            case "HIGH" -> "高风险";
            case "MEDIUM" -> "中风险";
            default -> "低风险";
        };
    }

    private String readableRiskCategory(String category) {
        return switch (normalizeRiskCategory(category, null, null, null)) {
            case "SUBJECT_INFO" -> "主体信息风险";
            case "PAYMENT" -> "付款风险";
            case "LIABILITY" -> "违约风险";
            case "TERM" -> "期限风险";
            case "DISPUTE_RESOLUTION" -> "争议解决风险";
            default -> "违约风险";
        };
    }

    private String riskUnderlineStyle(String level) {
        return switch (normalizeRiskLevel(level)) {
            case "HIGH" -> "color:#b91c1c;text-decoration:underline;font-weight:bold";
            case "MEDIUM" -> "color:#b45309;text-decoration:underline;font-weight:bold";
            default -> "color:#1d4ed8;text-decoration:underline";
        };
    }

    private String suggestionUnderlineStyle() {
        return "color:#15803d;text-decoration:underline;font-weight:bold";
    }

    private String nextVersionNo(Long contractId) {
        List<ContractVersion> versions = contractVersionMapper.selectList(
                new LambdaQueryWrapper<ContractVersion>().eq(ContractVersion::getContractId, contractId));
        int max = 0;
        Pattern pattern = Pattern.compile("^[vV]?(\\d+)(?:\\.0)?$");
        for (ContractVersion version : versions) {
            String value = Objects.toString(version.getVersionNo(), "").trim();
            Matcher matcher = pattern.matcher(value);
            if (matcher.matches()) {
                max = Math.max(max, Integer.parseInt(matcher.group(1)));
            }
        }
        return "V" + (max + 1) + ".0";
    }

    private void htmlRow(StringBuilder html, String label, Object value) {
        html.append("<tr><td>").append(escapeHtml(label)).append("</td><td>")
                .append(escapeHtml(Objects.toString(value, ""))).append("</td></tr>");
    }

    private int countValue(Integer value) {
        return value == null ? 0 : value;
    }

    private String safeAttachmentName(String value) {
        return Objects.toString(value, "未命名报告").replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String escapeHtml(Object value) {
        return Objects.toString(value, "")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String normalizeEscapedLineBreaks(String value) {
        return Objects.toString(value, "").replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\r", "\n");
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

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(Objects.toString(input, "").getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("内容哈希计算失败", ex);
        }
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
