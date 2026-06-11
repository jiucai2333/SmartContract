package cupk.smartcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cupk.smartcontract.entity.Approval;
import cupk.smartcontract.entity.ApprovalRecord;
import cupk.smartcontract.entity.ContractMain;
import cupk.smartcontract.dto.ApprovalVO;
import cupk.smartcontract.mapper.ApprovalInstanceMapper;
import cupk.smartcontract.mapper.ApprovalRecordMapper;
import cupk.smartcontract.mapper.ContractMainMapper;
import cupk.smartcontract.security.SecurityContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ApprovalService {

    private final ApprovalInstanceMapper mapper;
    private final ApprovalRecordMapper recordMapper;
    private final ContractMainMapper contractMapper;

    public ApprovalService(ApprovalInstanceMapper mapper,
                           ApprovalRecordMapper recordMapper,
                           ContractMainMapper contractMapper) {
        this.mapper = mapper;
        this.recordMapper = recordMapper;
        this.contractMapper = contractMapper;
    }

    public List<ApprovalVO> listApprovals() {
        return mapper.selectList(new LambdaQueryWrapper<Approval>()
                        .orderByDesc(Approval::getCreatedAt))
                .stream().map(this::toVo).toList();
    }

    @Transactional
    public ApprovalVO agree(Long instanceId, String comment) {
        Approval approval = mapper.selectById(instanceId);
        if (approval == null) {
            throw new IllegalArgumentException("审批实例不存在");
        }
        if (!"RUNNING".equals(approval.getStatus())) {
            throw new IllegalStateException("当前审批已结束");
        }

        ApprovalRecord record = new ApprovalRecord();
        record.setInstanceId(instanceId);
        record.setNodeName(approval.getCurrentNode() == null ? "审批" : approval.getCurrentNode());
        record.setApproverId(SecurityContext.userId());
        record.setAction("AGREE");
        record.setComment(comment);
        record.setActionTime(LocalDateTime.now());
        recordMapper.insert(record);

        approval.setStatus("APPROVED");
        approval.setEndedAt(LocalDateTime.now());
        approval.setUpdatedBy(SecurityContext.username());
        approval.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(approval);

        ContractMain contract = contractMapper.selectById(approval.getContractId());
        if (contract != null && "APPROVING".equals(contract.getStatus())) {
            contract.setStatus("APPROVED");
            contract.setUpdatedBy(SecurityContext.username());
            contract.setUpdatedAt(LocalDateTime.now());
            contractMapper.updateById(contract);
        }
        return toVo(approval);
    }

    private ApprovalVO toVo(Approval approval) {
        ContractMain contract = contractMapper.selectById(approval.getContractId());
        List<ApprovalRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<ApprovalRecord>()
                        .eq(ApprovalRecord::getInstanceId, approval.getInstanceId())
                        .orderByAsc(ApprovalRecord::getActionTime));
        return new ApprovalVO(
                approval.getInstanceId(),
                approval.getContractId(),
                contract == null ? null : contract.getContractNo(),
                contract == null ? null : contract.getTitle(),
                approval.getFlowType(),
                approval.getCurrentNode(),
                approval.getStatus(),
                approval.getStartedAt(),
                approval.getEndedAt(),
                records);
    }
}
