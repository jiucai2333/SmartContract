package cupk.smartcontract.controller;

import cupk.smartcontract.dto.ApprovalVO;
import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.security.AuditOperation;
import cupk.smartcontract.service.ApprovalService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/approvals")
    public List<ApprovalVO> listApprovals() {
        return approvalService.listApprovals();
    }

    @PostMapping("/approvals/{instanceId}/agree")
    @RequireRole({"DEPT_LEADER", "LEGAL", "EXECUTIVE", "ADMIN"})
    @AuditOperation(operation = "APPROVE_CONTRACT", targetType = "APPROVAL",
            targetIdParameter = "instanceId")
    public ApprovalVO agree(@PathVariable Long instanceId,
                            @RequestBody(required = false) Map<String, String> body) {
        return approvalService.agree(instanceId, body == null ? null : body.get("comment"));
    }

    @PostMapping("/approvals/{instanceId}/reject")
    @RequireRole({"DEPT_LEADER", "LEGAL", "EXECUTIVE", "ADMIN"})
    @AuditOperation(operation = "REJECT_CONTRACT", targetType = "APPROVAL",
            targetIdParameter = "instanceId")
    public ApprovalVO reject(@PathVariable Long instanceId,
                             @RequestBody(required = false) Map<String, String> body) {
        String comment = body == null ? null : body.get("comment");
        if (comment == null || comment.isBlank()) {
            comment = "驳回，请修改后重新提交";
        }
        return approvalService.reject(instanceId, comment);
    }
}
