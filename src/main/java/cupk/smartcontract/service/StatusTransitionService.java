package cupk.smartcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cupk.smartcontract.security.SecurityContext;
import cupk.smartcontract.entity.*;
import cupk.smartcontract.dto.ArchiveCreateRequest;
import cupk.smartcontract.dto.SealCreateRequest;
import cupk.smartcontract.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 合同状态流转服务。?
 * 缁熶竴缁存姢 contract_main.status 鐨勫悎娉曟祦杞鍒欍€?
 */
@Service
public class StatusTransitionService {

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "DRAFT",      Set.of("APPROVING"),
            "APPROVING",  Set.of("APPROVED", "DRAFT"),
            "APPROVED",   Set.of("SIGNING", "DRAFT"),
            "SIGNING",    Set.of("ARCHIVED"),
            "ARCHIVED",   Set.of("EXECUTING"),
            "EXECUTING",  Set.of("COMPLETED", "EXPIRED", "TERMINATED"),
            "COMPLETED",  Set.of(),
            "EXPIRED",    Set.of(),
            "TERMINATED", Set.of()
    );

    private final ContractMainMapper contractMapper;
    private final SealRecordMapper sealRecordMapper;
    private final ArchiveRecordMapper archiveRecordMapper;
    private final ContractVersionMapper versionMapper;
    private final OperationLogMapper operationLogMapper;

    public StatusTransitionService(ContractMainMapper contractMapper,
                                   SealRecordMapper sealRecordMapper,
                                   ArchiveRecordMapper archiveRecordMapper,
                                   ContractVersionMapper versionMapper,
                                   OperationLogMapper operationLogMapper) {
        this.contractMapper = contractMapper;
        this.sealRecordMapper = sealRecordMapper;
        this.archiveRecordMapper = archiveRecordMapper;
        this.versionMapper = versionMapper;
        this.operationLogMapper = operationLogMapper;
    }

    public void transition(ContractMain contract, String targetStatus) {
        String current = contract.getStatus();
        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(targetStatus)) {
            throw new IllegalStateException(
                    String.format("状态不允许从 %s 流转到 %s，可选目标：%s", current, targetStatus, String.join(", ", allowed)));
        }
        contract.setStatus(targetStatus);
        contract.setUpdatedAt(LocalDateTime.now());
        contractMapper.updateById(contract);
    }

    public void transitionToApproving(ContractMain contract) {
        transition(contract, "APPROVING");
    }

    public void transitionToApproved(ContractMain contract) {
        transition(contract, "APPROVED");
    }

    public void transitionToDraft(ContractMain contract) {
        transition(contract, "DRAFT");
    }

    @Transactional
    public SealRecord seal(SealCreateRequest request) {
        ContractMain contract = contractMapper.selectById(request.contractId());
        if (contract == null) throw new IllegalArgumentException("版本不属于此合同");
        ContractVersion version = versionMapper.selectById(request.versionId());
        if (version == null || !version.getContractId().equals(request.contractId()))
            throw new IllegalArgumentException("版本不属于此合同");

        SealRecord record = new SealRecord();
        record.setContractId(request.contractId());
        record.setVersionId(request.versionId());
        record.setFileId(request.fileId());
        record.setFileUrl(request.fileUrl());
        record.setFileName(request.fileName());
        String sealStatus = request.sealStatus();
        record.setSealStatus(sealStatus == null || sealStatus.isBlank() ? "ELECTRONIC" : sealStatus);
        record.setSealTime(request.sealTime() != null ? request.sealTime() : LocalDateTime.now());
        record.setOperatorId(SecurityContext.userId());
        record.setRemark(request.remark());
        record.setCreatedBy(SecurityContext.roleCode() + "_" + SecurityContext.userId());
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        record.setDeleted(0);
        record.setVersion(1);
        sealRecordMapper.insert(record);

        transition(contract, "SIGNING");
        return record;
    }

    @Transactional
    public ArchiveRecord archive(ArchiveCreateRequest request) {
        ContractMain contract = contractMapper.selectById(request.contractId());
        if (contract == null) throw new IllegalArgumentException("版本不属于此合同");
        ContractVersion version = versionMapper.selectById(request.versionId());
        if (version == null || !version.getContractId().equals(request.contractId()))
            throw new IllegalArgumentException("版本不属于此合同");

        String archiveNo = String.format("AR-%d-%d-%s",
                LocalDateTime.now().getYear(), request.contractId(),
                version.getVersionNo().replace(".", "-"));

        ArchiveRecord record = new ArchiveRecord();
        record.setContractId(request.contractId());
        record.setVersionId(request.versionId());
        record.setArchiveNo(archiveNo);
        record.setArchiveTime(LocalDateTime.now());
        record.setArchiverId(SecurityContext.userId());
        record.setIsLocked(true);
        record.setCreatedBy(SecurityContext.roleCode() + "_" + SecurityContext.userId());
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        record.setDeleted(0);
        record.setVersion(1);
        archiveRecordMapper.insert(record);

        version.setIsLocked(true);
        versionMapper.updateById(version);

        transition(contract, "ARCHIVED");
        return record;
    }

    public List<SealRecord> listSealRecords(Long contractId) {
        return sealRecordMapper.selectList(
                new LambdaQueryWrapper<SealRecord>()
                        .eq(SealRecord::getContractId, contractId)
                        .orderByDesc(SealRecord::getCreatedAt));
    }

    public List<ArchiveRecord> listArchiveRecords(Long contractId) {
        return archiveRecordMapper.selectList(
                new LambdaQueryWrapper<ArchiveRecord>()
                        .eq(ArchiveRecord::getContractId, contractId)
                        .orderByDesc(ArchiveRecord::getCreatedAt));
    }

    public boolean isVersionLocked(Long versionId) {
        ContractVersion version = versionMapper.selectById(versionId);
        return version != null && Boolean.TRUE.equals(version.getIsLocked());
    }

    public Set<Long> getLockedVersionIds(Long contractId) {
        List<ContractVersion> versions = versionMapper.selectList(
                new LambdaQueryWrapper<ContractVersion>()
                        .eq(ContractVersion::getContractId, contractId)
                        .eq(ContractVersion::getIsLocked, true));
        Set<Long> locked = new HashSet<>();
        for (ContractVersion v : versions) locked.add(v.getVersionId());
        return locked;
    }

    public void writeLog(Long userId, String operation, String targetType, Long targetId, String result) {
        OperationLog log = new OperationLog();
        log.setUserId(userId != null ? userId : SecurityContext.userId());
        log.setOperation(operation);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setIp("127.0.0.1");
        log.setResult(result);
        log.setCreatedAt(LocalDateTime.now());
        operationLogMapper.insert(log);
    }
}
