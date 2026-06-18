package cupk.smartcontract.controller;

import cupk.smartcontract.common.Result;
import cupk.smartcontract.dto.DeliverableRequest;
import cupk.smartcontract.dto.DeliverableVO;
import cupk.smartcontract.dto.FulfillmentPlanRequest;
import cupk.smartcontract.dto.FulfillmentPlanVO;
import cupk.smartcontract.dto.FulfillmentStats;
import cupk.smartcontract.dto.PaymentPlanRequest;
import cupk.smartcontract.dto.PaymentPlanVO;
import cupk.smartcontract.dto.PaymentRecordRequest;
import cupk.smartcontract.dto.PaymentRecordVO;
import cupk.smartcontract.dto.ReminderRecordVO;
import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.service.FulfillmentService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/fulfillment")
public class FulfillmentController {
    private final FulfillmentService service;

    public FulfillmentController(FulfillmentService service) {
        this.service = service;
    }

    @GetMapping("/plans")
    public List<FulfillmentPlanVO> plans(@RequestParam(required = false) Long contractId,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String keyword) {
        return service.listPlans(contractId, status, keyword);
    }

    @PostMapping("/plans/extract/{contractId}")
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    public List<FulfillmentPlanVO> extract(@PathVariable Long contractId) {
        return service.extractPlans(contractId);
    }

    @PostMapping("/contracts/{contractId}/member-e/init")
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "FINANCE", "ADMIN"})
    public Result initializeMemberE(@PathVariable Long contractId) {
        service.initializeMemberE(contractId);
        return Result.success();
    }

    @PostMapping("/plans")
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    public FulfillmentPlanVO create(@RequestBody FulfillmentPlanRequest request) {
        return service.createPlan(request);
    }

    @PutMapping("/plans/{planId}")
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    public FulfillmentPlanVO update(@PathVariable Long planId,
                                    @RequestBody FulfillmentPlanRequest request) {
        return service.updatePlan(planId, request);
    }

    @PostMapping("/plans/{planId}/handle-overdue")
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    public FulfillmentPlanVO handleOverdue(@PathVariable Long planId) {
        return service.handleOverdue(planId);
    }

    @PostMapping("/reminders/dispatch")
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    public List<ReminderRecordVO> dispatchReminders(@RequestParam(required = false) Long contractId) {
        return service.dispatchReminders(contractId);
    }

    @GetMapping("/reminders")
    public List<ReminderRecordVO> reminders(@RequestParam(required = false) Long contractId) {
        return service.listReminders(contractId);
    }

    @GetMapping("/stats")
    public FulfillmentStats stats(@RequestParam(required = false) Long contractId) {
        return service.stats(contractId);
    }

    @GetMapping("/deliverables")
    public List<DeliverableVO> deliverables(@RequestParam(required = false) Long contractId) {
        return service.listDeliverables(contractId);
    }

    @PutMapping("/deliverables/{deliverableId}")
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    public DeliverableVO updateDeliverable(@PathVariable Long deliverableId,
                                           @RequestBody DeliverableRequest request) {
        return service.updateDeliverable(deliverableId, request);
    }

    @PostMapping("/deliverables/{deliverableId}/confirm")
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    public DeliverableVO confirmDeliverable(@PathVariable Long deliverableId,
                                            @RequestParam(defaultValue = "true") boolean confirmed) {
        return service.confirmDeliverable(deliverableId, confirmed);
    }

    @GetMapping("/payments/plans")
    public List<PaymentPlanVO> paymentPlans(@RequestParam(required = false) Long contractId) {
        return service.listPaymentPlans(contractId);
    }

    @PostMapping("/payments/plans")
    @RequireRole({"USER", "DEPT_LEADER", "FINANCE", "ADMIN"})
    public PaymentPlanVO createPaymentPlan(@RequestBody PaymentPlanRequest request) {
        return service.createPaymentPlan(request);
    }

    @PutMapping("/payments/plans/{paymentPlanId}")
    @RequireRole({"USER", "DEPT_LEADER", "FINANCE", "ADMIN"})
    public PaymentPlanVO updatePaymentPlan(@PathVariable Long paymentPlanId,
                                           @RequestBody PaymentPlanRequest request) {
        return service.updatePaymentPlan(paymentPlanId, request);
    }

    @GetMapping("/payments/records")
    public List<PaymentRecordVO> paymentRecords(@RequestParam(required = false) Long contractId) {
        return service.listPaymentRecords(contractId);
    }

    @PostMapping("/payments/plans/{paymentPlanId}/records")
    @RequireRole({"USER", "DEPT_LEADER", "FINANCE", "ADMIN"})
    public PaymentRecordVO createPaymentRecord(@PathVariable Long paymentPlanId,
                                               @RequestBody PaymentRecordRequest request) {
        return service.createPaymentRecord(paymentPlanId, request);
    }

    @DeleteMapping("/payments/records/{recordId}")
    @RequireRole({"USER", "DEPT_LEADER", "FINANCE", "ADMIN"})
    public Result deletePaymentRecord(@PathVariable Long recordId) {
        service.deletePaymentRecord(recordId);
        return Result.success();
    }
}
