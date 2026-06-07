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
import java.util.Map;
import java.util.Set;

@Service
public class ApprovalService {

    private static final Map<String, List<String>> FLOW_NODES = Map.of(
            "NORMAL", List.of("部门主管审批"),
            "MAJOR", List.of("部门主管审批", "法务专员审批"),
            "SUPER", List.of("部门主管审批", "法务专员审批", "企业高管审批")
    );

    private static final Map<String, Set<String>> NODE_ROLES = Map.of(
            "部门主管审批", Set.of("DEPT_LEADER", "ADMIN"),
            "法务专员审批", Set.of("LEGAL", "ADMIN"),
            "企业高管审批", Set.of("EXECUTIVE", "ADMIN")
    );

    private final ApprovalInstanceMapper mapper;
    private final ApprovalRecordMapper recordMapper;
    private final ContractMainMapper contractMapper;
    private final StatusTransitionService statusTransitionService;

    public ApprovalService(ApprovalInstanceMapper mapper,
                           ApprovalRecordMapper recordMapper,
                           ContractMainMapper contractMapper,
                           StatusTransitionService statusTransitionService) {
        this.mapper = mapper;
        this.recordMapper = recordMapper;
        this.contractMapper = contractMapper;
        this.statusTransitionService = statusTransitionService;
    }

    public List<ApprovalVO> listApprovals() {
        return mapper.selectList(new LambdaQueryWrapper<Approval>()
                        .orderByDesc(Approval::getCreatedAt))
                .stream().map(this::toVo).toList();
    }

    @Transactional
    public ApprovalVO agree(Long instanceId, String comment) {
        Approval approval = requireRunning(instanceId);
        String currentNode = approval.getCurrentNode() == null ? "部门主管审批" : approval.getCurrentNode();
        assertNodeRole(currentNode);

        insertRecord(instanceId, currentNode, "AGREE", comment);

        List<String> nodes = flowNodes(approval.getFlowType());
        int idx = nodes.indexOf(currentNode);
        if (idx < 0) {
            throw new IllegalStateException("未知审批节点：" + currentNode);
        }

        if (idx < nodes.size() - 1) {
            approval.setCurrentNode(nodes.get(idx + 1));
            approval.setUpdatedBy(SecurityContext.username());
            approval.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(approval);
            statusTransitionService.writeLog(SecurityContext.userId(), "APPROVAL_AGREE",
                    "CONTRACT", approval.getContractId(), "NODE_ADVANCED");
            return toVo(approval);
        }

        approval.setStatus("APPROVED");
        approval.setEndedAt(LocalDateTime.now());
        approval.setUpdatedBy(SecurityContext.username());
        approval.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(approval);

        ContractMain contract = contractMapper.selectById(approval.getContractId());
        if (contract != null && "APPROVING".equals(contract.getStatus())) {
            statusTransitionService.transitionToApproved(contract);
            statusTransitionService.writeLog(SecurityContext.userId(), "APPROVAL_PASS",
                    "CONTRACT", approval.getContractId(), "SUCCESS");
        }
        return toVo(approval);
    }

    @Transactional
    public ApprovalVO reject(Long instanceId, String comment) {
        Approval approval = requireRunning(instanceId);
        String currentNode = approval.getCurrentNode() == null ? "部门主管审批" : approval.getCurrentNode();
        assertNodeRole(currentNode);

        insertRecord(instanceId, currentNode, "REJECT",
                comment == null || comment.isBlank() ? "驳回" : comment);

        approval.setStatus("REJECTED");
        approval.setEndedAt(LocalDateTime.now());
        approval.setUpdatedBy(SecurityContext.username());
        approval.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(approval);

        ContractMain contract = contractMapper.selectById(approval.getContractId());
        if (contract != null && "APPROVING".equals(contract.getStatus())) {
            statusTransitionService.transitionToDraft(contract);
            statusTransitionService.writeLog(SecurityContext.userId(), "APPROVAL_REJECT",
                    "CONTRACT", approval.getContractId(), "SUCCESS");
        }
        return toVo(approval);
    }

    private Approval requireRunning(Long instanceId) {
        Approval approval = mapper.selectById(instanceId);
        if (approval == null) {
            throw new IllegalArgumentException("审批实例不存在");
        }
        if (!"RUNNING".equals(approval.getStatus())) {
            throw new IllegalStateException("当前审批已结束");
        }
        return approval;
    }

    private void assertNodeRole(String nodeName) {
        Set<String> allowed = NODE_ROLES.get(nodeName);
        if (allowed == null || !allowed.contains(SecurityContext.roleCode())) {
            throw new IllegalStateException("当前角色无权处理节点：" + nodeName);
        }
    }

    private List<String> flowNodes(String flowType) {
        return FLOW_NODES.getOrDefault(flowType, FLOW_NODES.get("NORMAL"));
    }

    private void insertRecord(Long instanceId, String nodeName, String action, String comment) {
        ApprovalRecord record = new ApprovalRecord();
        record.setInstanceId(instanceId);
        record.setNodeName(nodeName);
        record.setApproverId(SecurityContext.userId());
        record.setAction(action);
        record.setComment(comment);
        record.setActionTime(LocalDateTime.now());
        recordMapper.insert(record);
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
