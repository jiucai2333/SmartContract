package cupk.smartcontract.controller;

import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.entity.ContractMain;
import cupk.smartcontract.dto.AiDraftRequest;
import cupk.smartcontract.dto.AiDraftResponse;
import cupk.smartcontract.dto.ContractCreateRequest;
import cupk.smartcontract.service.AiDraftService;
import cupk.smartcontract.service.ContractManagementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class ContractController {
    private final ContractManagementService service;
    private final AiDraftService aiDraftService;

    public ContractController(ContractManagementService service, AiDraftService aiDraftService) {
        this.service = service;
        this.aiDraftService = aiDraftService;
    }

    @GetMapping("/contracts")
    public Object contracts(@RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) String riskLevel,
                            @RequestParam(required = false) String type) {
        return service.listContracts(keyword, status, riskLevel, type);
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    @PostMapping("/contracts")
    public ContractMain createContract(@Valid @RequestBody ContractCreateRequest request) {
        return service.createContract(request);
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    @PutMapping("/contracts/{contractId}")
    public ContractMain updateContract(@PathVariable Long contractId,
                                       @Valid @RequestBody ContractCreateRequest request) {
        return service.updateContract(contractId, request);
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    @PostMapping("/ai/draft")
    public AiDraftResponse draft(@Valid @RequestBody AiDraftRequest request) {
        return service.generateDraft(request);
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    @PostMapping("/ai/draft-stream")
    public SseEmitter draftStream(@Valid @RequestBody AiDraftRequest request) {
        return aiDraftService.streamDraft(request);
    }

}
