package cupk.smartcontract.controller;

import cupk.smartcontract.common.Result;
import cupk.smartcontract.dto.DelayApprovalDecisionRequest;
import cupk.smartcontract.dto.DeliverableRequest;
import cupk.smartcontract.dto.DeliverableTransitionRequest;
import cupk.smartcontract.dto.DeliverableVO;
import cupk.smartcontract.dto.FulfillmentDelayApprovalVO;
import cupk.smartcontract.dto.FulfillmentProgressLogVO;
import cupk.smartcontract.dto.FulfillmentPlanRequest;
import cupk.smartcontract.dto.FulfillmentPlanVO;
import cupk.smartcontract.dto.FulfillmentStats;
import cupk.smartcontract.dto.FulfillmentVoucherVO;
import cupk.smartcontract.dto.InvoiceRecordRequest;
import cupk.smartcontract.dto.InvoiceRecordVO;
import cupk.smartcontract.dto.OverdueHandleRequest;
import cupk.smartcontract.dto.PaymentPlanRequest;
import cupk.smartcontract.dto.PaymentPlanVO;
import cupk.smartcontract.dto.PaymentRecordRequest;
import cupk.smartcontract.dto.PaymentRecordVO;
import cupk.smartcontract.dto.ReminderRecordVO;
import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.service.FulfillmentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    @RequireRole({"USER", "DEPT_LEADER", "ADMIN"})
    public FulfillmentPlanVO create(@RequestBody FulfillmentPlanRequest request,
                                    HttpServletRequest servletRequest) {
        return service.createPlan(request, clientIp(servletRequest));
    }

    @PutMapping("/plans/{planId}")
    @RequireRole({"USER", "DEPT_LEADER", "ADMIN"})
    public FulfillmentPlanVO update(@PathVariable Long planId,
                                    @RequestBody FulfillmentPlanRequest request,
                                    HttpServletRequest servletRequest) {
        return service.updatePlan(planId, request, clientIp(servletRequest));
    }

    @DeleteMapping("/plans/{planId}")
    @RequireRole({"USER", "DEPT_LEADER", "ADMIN"})
    public Result deletePlan(@PathVariable Long planId,
                             HttpServletRequest servletRequest) {
        service.deletePlan(planId, clientIp(servletRequest));
        return Result.success();
    }

    @PostMapping("/plans/{planId}/delay/confirm")
    @RequireRole({"DEPT_LEADER", "ADMIN"})
    public FulfillmentPlanVO confirmDelay(@PathVariable Long planId,
                                          HttpServletRequest servletRequest) {
        return service.confirmDelay(planId, clientIp(servletRequest));
    }

    @PostMapping("/plans/{planId}/handle-overdue")
    @RequireRole({"USER", "DEPT_LEADER", "ADMIN"})
    public FulfillmentPlanVO handleOverdue(@PathVariable Long planId,
                                           @RequestBody(required = false) OverdueHandleRequest request,
                                           HttpServletRequest servletRequest) {
        return service.handleOverdue(planId, request, clientIp(servletRequest));
    }

    @GetMapping("/progress-logs")
    public List<FulfillmentProgressLogVO> progressLogs(@RequestParam(required = false) Long contractId,
                                                       @RequestParam(required = false) Long planId) {
        return service.listProgressLogs(contractId, planId);
    }

    @GetMapping("/vouchers")
    public List<FulfillmentVoucherVO> vouchers(@RequestParam(required = false) Long contractId,
                                               @RequestParam(required = false) Long planId) {
        return service.listVouchers(contractId, planId);
    }

    @GetMapping("/delay-approvals")
    public List<FulfillmentDelayApprovalVO> delayApprovals(@RequestParam(required = false) Long contractId,
                                                           @RequestParam(required = false) Long planId,
                                                           @RequestParam(required = false) String status) {
        return service.listDelayApprovals(contractId, planId, status);
    }

    @PostMapping("/delay-approvals/{approvalId}/review")
    @RequireRole({"DEPT_LEADER", "ADMIN"})
    public FulfillmentDelayApprovalVO reviewDelayApproval(@PathVariable Long approvalId,
                                                          @RequestBody DelayApprovalDecisionRequest request,
                                                          HttpServletRequest servletRequest) {
        return service.reviewDelayApproval(approvalId, request, clientIp(servletRequest));
    }

    @PostMapping("/plans/{planId}/vouchers")
    @RequireRole({"USER", "DEPT_LEADER", "ADMIN"})
    public FulfillmentVoucherVO uploadVoucher(@PathVariable Long planId,
                                              @RequestParam("file") MultipartFile file,
                                              @RequestParam(defaultValue = "PROGRESS") String voucherType,
                                              @RequestParam(required = false) String remark,
                                              HttpServletRequest servletRequest) throws Exception {
        return service.uploadVoucher(planId, file, voucherType, remark, clientIp(servletRequest));
    }

    @PostMapping("/vouchers/{voucherId}/review")
    @RequireRole({"FINANCE", "DEPT_LEADER", "ADMIN"})
    public FulfillmentVoucherVO reviewVoucher(@PathVariable Long voucherId,
                                              @RequestParam(defaultValue = "true") boolean approved,
                                              @RequestParam(required = false) String remark,
                                              HttpServletRequest servletRequest) {
        return service.reviewVoucher(voucherId, approved, remark, clientIp(servletRequest));
    }

    @GetMapping("/vouchers/{voucherId}/download")
    public ResponseEntity<Resource> downloadVoucher(@PathVariable Long voucherId) {
        return service.downloadVoucher(voucherId);
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
    public List<DeliverableVO> deliverables(@RequestParam(required = false) Long contractId,
                                            @RequestParam(required = false) Long planId) {
        return service.listDeliverables(contractId, planId);
    }

    @PostMapping("/deliverables")
    @RequireRole({"USER", "DEPT_LEADER", "ADMIN"})
    public DeliverableVO createDeliverable(@RequestBody DeliverableRequest request,
                                           HttpServletRequest servletRequest) {
        return service.createDeliverable(request, clientIp(servletRequest));
    }

    @PutMapping("/deliverables/{deliverableId}")
    @RequireRole({"USER", "DEPT_LEADER", "ADMIN"})
    public DeliverableVO updateDeliverable(@PathVariable Long deliverableId,
                                           @RequestBody DeliverableRequest request,
                                           HttpServletRequest servletRequest) {
        return service.updateDeliverable(deliverableId, request, clientIp(servletRequest));
    }

    @PostMapping("/deliverables/{deliverableId}/transition")
    @RequireRole({"USER", "DEPT_LEADER", "ADMIN"})
    public DeliverableVO transitionDeliverable(@PathVariable Long deliverableId,
                                               @RequestBody DeliverableTransitionRequest request,
                                               HttpServletRequest servletRequest) {
        return service.transitionDeliverable(deliverableId, request, clientIp(servletRequest));
    }

    @PostMapping("/deliverables/{deliverableId}/confirm")
    @RequireRole({"DEPT_LEADER", "ADMIN"})
    public DeliverableVO confirmDeliverable(@PathVariable Long deliverableId,
                                            @RequestParam(defaultValue = "true") boolean confirmed,
                                            HttpServletRequest servletRequest) {
        return service.confirmDeliverable(deliverableId, confirmed, clientIp(servletRequest));
    }

    @PostMapping("/deliverables/{deliverableId}/file")
    @RequireRole({"USER", "DEPT_LEADER", "ADMIN"})
    public DeliverableVO uploadDeliverableFile(@PathVariable Long deliverableId,
                                               @RequestParam("file") MultipartFile file,
                                               @RequestParam(required = false) String remark,
                                               HttpServletRequest servletRequest) throws Exception {
        return service.uploadDeliverableFile(deliverableId, file, remark, clientIp(servletRequest));
    }

    @GetMapping("/deliverables/{deliverableId}/file")
    public ResponseEntity<Resource> downloadDeliverableFile(@PathVariable Long deliverableId) {
        return service.downloadDeliverableFile(deliverableId);
    }

    @DeleteMapping("/deliverables/{deliverableId}")
    @RequireRole({"USER", "DEPT_LEADER", "ADMIN"})
    public Result deleteDeliverable(@PathVariable Long deliverableId,
                                    HttpServletRequest servletRequest) {
        service.deleteDeliverable(deliverableId, clientIp(servletRequest));
        return Result.success();
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

    @DeleteMapping("/payments/plans/{paymentPlanId}")
    @RequireRole({"USER", "DEPT_LEADER", "FINANCE", "ADMIN"})
    public Result deletePaymentPlan(@PathVariable Long paymentPlanId) {
        service.deletePaymentPlan(paymentPlanId);
        return Result.success();
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

    @PostMapping("/payments/records/{recordId}/voucher")
    @RequireRole({"USER", "DEPT_LEADER", "FINANCE", "ADMIN"})
    public PaymentRecordVO uploadPaymentVoucher(@PathVariable Long recordId,
                                                @RequestParam("file") MultipartFile file,
                                                @RequestParam(required = false) String remark) throws Exception {
        return service.uploadPaymentVoucher(recordId, file, remark);
    }

    @GetMapping("/payments/records/{recordId}/voucher")
    public ResponseEntity<Resource> downloadPaymentVoucher(@PathVariable Long recordId) {
        return service.downloadPaymentVoucher(recordId);
    }

    @DeleteMapping("/payments/records/{recordId}")
    @RequireRole({"USER", "DEPT_LEADER", "FINANCE", "ADMIN"})
    public Result deletePaymentRecord(@PathVariable Long recordId) {
        service.deletePaymentRecord(recordId);
        return Result.success();
    }

    @GetMapping("/payments/invoices")
    public List<InvoiceRecordVO> invoices(@RequestParam(required = false) Long contractId,
                                          @RequestParam(required = false) Long paymentPlanId) {
        return service.listInvoices(contractId, paymentPlanId);
    }

    @PostMapping("/payments/plans/{paymentPlanId}/invoices")
    @RequireRole({"USER", "DEPT_LEADER", "FINANCE", "ADMIN"})
    public InvoiceRecordVO createInvoice(@PathVariable Long paymentPlanId,
                                         @RequestBody InvoiceRecordRequest request) {
        return service.createInvoice(paymentPlanId, request);
    }

    @PostMapping("/payments/invoices/{invoiceId}/file")
    @RequireRole({"USER", "DEPT_LEADER", "FINANCE", "ADMIN"})
    public InvoiceRecordVO uploadInvoiceFile(@PathVariable Long invoiceId,
                                             @RequestParam("file") MultipartFile file,
                                             @RequestParam(required = false) String remark) throws Exception {
        return service.uploadInvoiceFile(invoiceId, file, remark);
    }

    @GetMapping("/payments/invoices/{invoiceId}/file")
    public ResponseEntity<Resource> downloadInvoiceFile(@PathVariable Long invoiceId) {
        return service.downloadInvoiceFile(invoiceId);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp != null && !realIp.isBlank() ? realIp : request.getRemoteAddr();
    }
}
