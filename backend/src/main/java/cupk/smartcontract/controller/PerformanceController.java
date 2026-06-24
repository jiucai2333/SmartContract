package cupk.smartcontract.controller;

import cupk.smartcontract.dto.*;
import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.service.PerformanceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/performance")
public class PerformanceController {
    private final PerformanceService service;

    public PerformanceController(PerformanceService service) {
        this.service = service;
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    @PostMapping("/deliverables")
    public DeliverableVO saveDeliverable(@Valid @RequestBody DeliverableRequest request) {
        return service.saveDeliverable(request);
    }

    @GetMapping("/deliverables")
    public List<DeliverableVO> listDeliverables(@RequestParam Long contractId) {
        return service.listDeliverables(contractId);
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    @PutMapping("/deliverables/{id}/confirm")
    public DeliverableVO confirmDeliverable(@PathVariable Long id,
                                            @RequestBody ConfirmDeliverableRequest request) {
        return service.confirmDeliverable(id, request.getConfirmedBy());
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    @PutMapping("/deliverables/{id}/unconfirm")
    public DeliverableVO unconfirmDeliverable(@PathVariable Long id) {
        return service.unconfirmDeliverable(id);
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    @DeleteMapping("/deliverables/{id}")
    public String deleteDeliverable(@PathVariable Long id) {
        service.deleteDeliverable(id);
        return "ok";
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    @PostMapping("/payment-plans")
    public PaymentPlanVO savePaymentPlan(@Valid @RequestBody PaymentPlanRequest request) {
        return service.savePaymentPlan(request);
    }

    @GetMapping("/payment-plans")
    public List<PaymentPlanVO> listPaymentPlans(@RequestParam Long contractId) {
        return service.listPaymentPlans(contractId);
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    @DeleteMapping("/payment-plans/{id}")
    public String deletePaymentPlan(@PathVariable Long id) {
        service.deletePaymentPlan(id);
        return "ok";
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    @PostMapping("/payment-records")
    public PaymentRecordVO savePaymentRecord(@Valid @RequestBody PaymentRecordRequest request) {
        return service.recordPayment(request);
    }

    @GetMapping("/payment-records")
    public List<PaymentRecordVO> listPaymentRecords(@RequestParam Long contractId) {
        return service.listPaymentRecords(contractId);
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    @DeleteMapping("/payment-records/{id}")
    public String deletePaymentRecord(@PathVariable Long id) {
        service.deletePaymentRecord(id);
        return "ok";
    }

    @GetMapping("/overdue-days/{id}")
    public long overdueDays(@PathVariable Long id) {
        return service.getOverdueDays(id);
    }

    @GetMapping("/liability-hint/{id}")
    public String liabilityHint(@PathVariable Long id) {
        return service.getLiabilityHint(id);
    }

    @GetMapping("/overdue-list")
    public List<OverdueItemVO> overdueList() {
        return service.listOverdueList();
    }

    @GetMapping("/progress/{contractId}")
    public PerformanceProgressVO progress(@PathVariable Long contractId) {
        return service.getProgress(contractId);
    }

    @GetMapping("/plans")
    public List<FulfillmentPlanVO> plans(@RequestParam Long contractId) {
        return service.listFulfillmentPlans(contractId);
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    @PutMapping("/plans/{planId}/status")
    public FulfillmentPlanVO updatePlanStatus(@PathVariable Long planId,
                                              @RequestBody Map<String, String> body) {
        return service.updatePlanStatus(planId, body.get("status"), body.get("notes"));
    }

    @GetMapping("/contracts")
    public List<ContractSummaryVO> contracts() {
        return service.listActiveContracts();
    }
}
