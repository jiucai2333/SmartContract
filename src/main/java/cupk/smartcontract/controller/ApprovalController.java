package cupk.smartcontract.controller;

import cupk.smartcontract.dto.ApprovalVO;
import cupk.smartcontract.security.RequireRole;
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
    public ApprovalVO agree(@PathVariable Long instanceId,
                            @RequestBody(required = false) Map<String, String> body) {
        return approvalService.agree(instanceId, body == null ? null : body.get("comment"));
    }
}
