package cupk.smartcontract.controller;

import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.entity.ContractMain;
import cupk.smartcontract.dto.*;
import cupk.smartcontract.service.AiDraftService;
import cupk.smartcontract.service.ContractManagementService;
import cupk.smartcontract.service.PerformanceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ContractController {
    private final ContractManagementService service;
    private final AiDraftService aiDraftService;
    private final PerformanceService performanceService;

    public ContractController(ContractManagementService service,
                              AiDraftService aiDraftService,
                              PerformanceService performanceService) {
        this.service = service;
        this.aiDraftService = aiDraftService;
        this.performanceService = performanceService;
    }

    // ==================== Contracts ====================

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

    // ==================== Performance: Deliverables ====================

    @PostMapping("/performance/deliverables")
    public DeliverableVO createDeliverable(@Valid @RequestBody DeliverableRequest request) {
        return performanceService.saveDeliverable(request);
    }

    @GetMapping("/performance/deliverables")
    public List<DeliverableVO> listDeliverables(@RequestParam Long contractId) {
        return performanceService.listDeliverables(contractId);
    }

    @PutMapping("/performance/deliverables/{id}/confirm")
    public DeliverableVO confirmDeliverable(@PathVariable Long id,
                                            @Valid @RequestBody ConfirmDeliverableRequest request) {
        return performanceService.confirmDeliverable(id, request.getConfirmedBy());
    }

    @PutMapping("/performance/deliverables/{id}/unconfirm")
    public DeliverableVO unconfirmDeliverable(@PathVariable Long id) {
        return performanceService.unconfirmDeliverable(id);
    }

    @DeleteMapping("/performance/deliverables/{id}")
    public String deleteDeliverable(@PathVariable Long id) {
        performanceService.deleteDeliverable(id);
        return "ok";
    }

    // ==================== Performance: Payment Plans ====================

    @PostMapping("/performance/payment-plans")
    public PaymentPlanVO createPaymentPlan(@Valid @RequestBody PaymentPlanRequest request) {
        return performanceService.savePaymentPlan(request);
    }

    @GetMapping("/performance/payment-plans")
    public List<PaymentPlanVO> listPaymentPlans(@RequestParam Long contractId) {
        return performanceService.listPaymentPlans(contractId);
    }

    @DeleteMapping("/performance/payment-plans/{id}")
    public String deletePaymentPlan(@PathVariable Long id) {
        performanceService.deletePaymentPlan(id);
        return "ok";
    }

    // ==================== Performance: Payment Records ====================

    @PostMapping("/performance/payment-records")
    public PaymentRecordVO createPaymentRecord(@Valid @RequestBody PaymentRecordRequest request) {
        return performanceService.recordPayment(request);
    }

    @GetMapping("/performance/payment-records")
    public List<PaymentRecordVO> listPaymentRecords(@RequestParam Long contractId) {
        return performanceService.listPaymentRecords(contractId);
    }

    @DeleteMapping("/performance/payment-records/{id}")
    public String deletePaymentRecord(@PathVariable Long id) {
        performanceService.deletePaymentRecord(id);
        return "ok";
    }

    // ==================== Performance: Overdue & Liability ====================

    @GetMapping("/performance/overdue-days/{id}")
    public Long getOverdueDays(@PathVariable Long id) {
        return performanceService.getOverdueDays(id);
    }

    @GetMapping("/performance/liability-hint/{id}")
    public String getLiabilityHint(@PathVariable Long id) {
        return performanceService.getLiabilityHint(id);
    }

    @GetMapping("/performance/overdue-list")
    public List<OverdueItemVO> listOverdueList() {
        return performanceService.listOverdueList();
    }

    // ==================== Performance: Progress ====================

    @GetMapping("/performance/progress/{contractId}")
    public PerformanceProgressVO getProgress(@PathVariable Long contractId) {
        return performanceService.getProgress(contractId);
    }

    // ==================== Performance: Fulfillment Plans ====================

    @GetMapping("/performance/plans")
    public List<FulfillmentPlanVO> listFulfillmentPlans(@RequestParam Long contractId) {
        return performanceService.listFulfillmentPlans(contractId);
    }

    @PutMapping("/performance/plans/{planId}/status")
    public FulfillmentPlanVO updatePlanStatus(@PathVariable Long planId,
                                              @RequestBody java.util.Map<String, String> body) {
        return performanceService.updatePlanStatus(planId, body.get("status"), body.get("notes"));
    }

    // ==================== Common ====================

    @GetMapping("/performance/contracts")
    public List<ContractSummaryVO> listActiveContracts() {
        return performanceService.listActiveContracts();
    }
}
