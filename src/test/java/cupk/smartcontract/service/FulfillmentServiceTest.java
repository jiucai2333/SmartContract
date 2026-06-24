package cupk.smartcontract.service;

import cupk.smartcontract.dto.AuthUserVO;
import cupk.smartcontract.dto.FulfillmentPlanRequest;
import cupk.smartcontract.dto.FulfillmentPlanVO;
import cupk.smartcontract.entity.ContractMain;
import cupk.smartcontract.entity.FulfillmentPlan;
import cupk.smartcontract.entity.FulfillmentDeliverable;
import cupk.smartcontract.security.SecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FulfillmentServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContext.clear();
    }

    @Test
    void warningLevelUsesExactThirtySevenOneDayThresholds() throws Exception {
        FulfillmentService service = service();
        Method method = FulfillmentService.class.getDeclaredMethod("warningLevel", FulfillmentPlan.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, planDueIn(30))).isEqualTo("LEVEL1");
        assertThat(method.invoke(service, planDueIn(29))).isEqualTo("NORMAL");
        assertThat(method.invoke(service, planDueIn(7))).isEqualTo("LEVEL2");
        assertThat(method.invoke(service, planDueIn(6))).isEqualTo("NORMAL");
        assertThat(method.invoke(service, planDueIn(1))).isEqualTo("LEVEL3");
        assertThat(method.invoke(service, planDueIn(0))).isEqualTo("NORMAL");
        assertThat(method.invoke(service, planDueIn(-1))).isEqualTo("OVERDUE");
    }

    @Test
    void warningLevelIgnoresPendingConfirmationPlans() throws Exception {
        Method method = FulfillmentService.class.getDeclaredMethod("warningLevel", FulfillmentPlan.class);
        method.setAccessible(true);

        FulfillmentPlan plan = planDueIn(1);
        plan.setStatus("PENDING_CONFIRM");
        plan.setConfirmStatus("PENDING_CONFIRM");

        assertThat(method.invoke(service(), plan)).isEqualTo("NONE");
    }

    @Test
    void extendingDueDateCreatesDelayRequestOnlyWhenPlanIsOpen() throws Exception {
        Method method = FulfillmentService.class.getDeclaredMethod(
                "isDelayRequest", FulfillmentPlan.class, FulfillmentPlanRequest.class);
        method.setAccessible(true);

        FulfillmentPlan plan = planDueIn(7);
        FulfillmentPlanRequest extension = request(LocalDate.now().plusDays(10), "reason");
        FulfillmentPlanRequest sameDate = request(plan.getDueDate(), "reason");

        assertThat(method.invoke(service(), plan, extension)).isEqualTo(true);
        assertThat(method.invoke(service(), plan, sameDate)).isEqualTo(false);

        plan.setStatus("COMPLETED");
        assertThat(method.invoke(service(), plan, extension)).isEqualTo(false);
    }

    @Test
    void delayRequestRequiresReasonAndKeepsRequestedDateForApproval() throws Exception {
        Method method = FulfillmentService.class.getDeclaredMethod(
                "requestDelay", FulfillmentPlan.class, LocalDate.class, String.class);
        method.setAccessible(true);
        FulfillmentPlan plan = planDueIn(7);
        LocalDate requestedDate = LocalDate.now().plusDays(14);

        assertThatThrownBy(() -> method.invoke(service(), plan, requestedDate, " "))
                .hasCauseInstanceOf(IllegalArgumentException.class);

        SecurityContext.set(AuthUserVO.of(1L, "deptUser", 10L, "USER", "SELF"));
        method.invoke(service(), plan, requestedDate, "Need more acceptance time");

        assertThat(plan.getDueDate()).isEqualTo(LocalDate.now().plusDays(7));
        assertThat(plan.getDelayStatus()).isEqualTo("PENDING");
        assertThat(plan.getDelayRequestedDueDate()).isEqualTo(requestedDate);
        assertThat(plan.getDelayReason()).isEqualTo("Need more acceptance time");
        assertThat(plan.getDelayRequestedBy()).isEqualTo("deptUser");
        assertThat(plan.getDelayConfirmedBy()).isNull();
        assertThat(plan.getDelayConfirmedAt()).isNull();
    }

    @Test
    void onlyDepartmentLeaderOrAdminCanConfirmDelay() throws Exception {
        Method method = FulfillmentService.class.getDeclaredMethod("canConfirmDelay");
        method.setAccessible(true);

        SecurityContext.set(AuthUserVO.of(1L, "user", 10L, "USER", "SELF"));
        assertThat(method.invoke(service())).isEqualTo(false);

        SecurityContext.set(AuthUserVO.of(2L, "leader", 10L, "DEPT_LEADER", "DEPT"));
        assertThat(method.invoke(service())).isEqualTo(true);

        SecurityContext.set(AuthUserVO.of(3L, "admin", 10L, "ADMIN", "ALL"));
        assertThat(method.invoke(service())).isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void reminderChannelsApplyNotificationMatrix() throws Exception {
        Method method = FulfillmentService.class.getDeclaredMethod("reminderChannels", String.class);
        method.setAccessible(true);

        assertThat((List<String>) method.invoke(service(), "LEVEL1"))
                .containsExactly("IN_APP");
        assertThat((List<String>) method.invoke(service(), "LEVEL2"))
                .containsExactly("IN_APP", "EMAIL", "WECHAT");
        assertThat((List<String>) method.invoke(service(), "LEVEL3"))
                .containsExactly("IN_APP", "EMAIL", "WECHAT", "SMS");
        assertThat((List<String>) method.invoke(service(), "OVERDUE"))
                .containsExactly("IN_APP", "EMAIL", "WECHAT", "SMS");
    }

    @Test
    void pendingDelayPlansAreSuppressedFromReminderDispatch() throws Exception {
        Method method = FulfillmentService.class.getDeclaredMethod("reminderSuppressedForDelay", FulfillmentPlanVO.class);
        method.setAccessible(true);

        assertThat(method.invoke(service(), planVo("LEVEL3", "PENDING"))).isEqualTo(true);
        assertThat(method.invoke(service(), planVo("LEVEL3", "APPROVED"))).isEqualTo(false);
        assertThat(method.invoke(service(), planVo("LEVEL3", "REJECTED"))).isEqualTo(false);
        assertThat(method.invoke(service(), planVo("LEVEL3", "NONE"))).isEqualTo(false);
    }

    @Test
    void reminderLogOnlyAllowsThreeWarningLevels() throws Exception {
        Method method = FulfillmentService.class.getDeclaredMethod("isReminderWarningLevel", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service(), "LEVEL1")).isEqualTo(true);
        assertThat(method.invoke(service(), "LEVEL2")).isEqualTo(true);
        assertThat(method.invoke(service(), "LEVEL3")).isEqualTo(true);
        assertThat(method.invoke(service(), "OVERDUE")).isEqualTo(false);
        assertThat(method.invoke(service(), "DELAY_APPROVAL")).isEqualTo(false);
        assertThat(method.invoke(service(), "DELAY_APPROVED")).isEqualTo(false);
        assertThat(method.invoke(service(), "DELAY_REJECTED")).isEqualTo(false);
    }

    @Test
    void progressOperationCodesAreDisplayedInChinese() throws Exception {
        Method method = FulfillmentService.class.getDeclaredMethod("progressOperationText", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service(), "OVERDUE_COMPLETE")).isEqualTo("逾期标记完成");
        assertThat(method.invoke(service(), "OVERDUE_DELAY_REQUEST")).isEqualTo("逾期申请延期");
        assertThat(method.invoke(service(), "DELAY_APPROVE")).isEqualTo("延期审批通过");
        assertThat(method.invoke(service(), "DELAY_REJECT")).isEqualTo("延期审批驳回");
        assertThat(method.invoke(service(), "FULFILLMENT_DELIVERABLE_FILE_UPLOAD")).isEqualTo("上传交付物文件");
        assertThat(method.invoke(service(), "FULFILLMENT_UNKNOWN_ACTION")).isEqualTo("系统操作");
        assertThat(method.invoke(service(), "凭证上传")).isEqualTo("凭证上传");
    }

    @Test
    void paymentStatusUsesRequiredWorkflowStates() throws Exception {
        Method method = FulfillmentService.class.getDeclaredMethod(
                "paymentStatus", cupk.smartcontract.entity.PaymentPlan.class, BigDecimal.class, BigDecimal.class, long.class, boolean.class);
        method.setAccessible(true);

        assertThat(method.invoke(service(), null, new BigDecimal("100"), BigDecimal.ZERO, 0L, false))
                .isEqualTo("WAIT_CONDITION");
        assertThat(method.invoke(service(), null, new BigDecimal("100"), BigDecimal.ZERO, 0L, true))
                .isEqualTo("READY_TO_PAY");
        assertThat(method.invoke(service(), null, new BigDecimal("100"), new BigDecimal("20"), 0L, true))
                .isEqualTo("PARTIAL_PAID");
        assertThat(method.invoke(service(), null, new BigDecimal("100"), new BigDecimal("100"), 0L, true))
                .isEqualTo("PAID");
        assertThat(method.invoke(service(), null, new BigDecimal("100"), BigDecimal.ZERO, 2L, true))
                .isEqualTo("OVERDUE");
    }

    @Test
    void standardPaymentPlansAreOnlyUsedWhenAiPaymentNodesAreMissing() throws Exception {
        Method method = FulfillmentService.class.getDeclaredMethod(
                "shouldCreateStandardPaymentPlans", List.class);
        method.setAccessible(true);

        FulfillmentPlan aiPaymentNode = new FulfillmentPlan();
        aiPaymentNode.setSourceType("AI");
        aiPaymentNode.setPlanType("PAYMENT");

        assertThat(method.invoke(service(), List.of())).isEqualTo(true);
        assertThat(method.invoke(service(), new Object[]{null})).isEqualTo(true);
        assertThat(method.invoke(service(), List.of(aiPaymentNode))).isEqualTo(false);
    }

    @Test
    void paymentPlanDeletionRejectsExistingFinancialRecords() throws Exception {
        Method method = FulfillmentService.class.getDeclaredMethod(
                "assertPaymentPlanDeletable", long.class, long.class);
        method.setAccessible(true);

        method.invoke(service(), 0L, 0L);

        assertThatThrownBy(() -> method.invoke(service(), 1L, 0L))
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("该付款计划已有到账记录或发票材料，不能直接删除；请先删除相关记录");
        assertThatThrownBy(() -> method.invoke(service(), 0L, 1L))
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("该付款计划已有到账记录或发票材料，不能直接删除；请先删除相关记录");
    }

    @Test
    void paymentAmountParsesExplicitRmbAndChineseUnits() throws Exception {
        Method method = FulfillmentService.class.getDeclaredMethod(
                "paymentAmountFromNode", FulfillmentPlan.class, String.class);
        method.setAccessible(true);

        FulfillmentPlan commaAmount = new FulfillmentPlan();
        commaAmount.setNodeName("首付款支付");
        commaAmount.setSourceClause("甲方应支付首付款人民币20,000元");
        assertThat(method.invoke(service(), commaAmount, "")).isEqualTo(new BigDecimal("20000.00"));

        FulfillmentPlan tenThousandUnit = new FulfillmentPlan();
        tenThousandUnit.setNodeName("中期款支付");
        tenThousandUnit.setSourceClause("中期款为3万元");
        assertThat(method.invoke(service(), tenThousandUnit, "")).isEqualTo(new BigDecimal("30000.00"));

        FulfillmentPlan symbolAmount = new FulfillmentPlan();
        symbolAmount.setNodeName("尾款支付");
        symbolAmount.setSourceClause("尾款￥50000");
        assertThat(method.invoke(service(), symbolAmount, "")).isEqualTo(new BigDecimal("50000.00"));
    }

    @Test
    void paymentAmountUsesClauseLocationToReadOriginalArchivedText() throws Exception {
        Method method = FulfillmentService.class.getDeclaredMethod(
                "paymentAmountFromNode", FulfillmentPlan.class, String.class);
        method.setAccessible(true);

        FulfillmentPlan node = new FulfillmentPlan();
        node.setNodeName("首期款支付");
        node.setSourceClause("第五条");
        String originalText = """
                合同总金额：人民币100,000元。
                五、首期付款
                甲方应支付首期款人民币20,000元。
                节点名称：首期款支付。
                六、中期付款
                甲方应支付中期款人民币30,000元。
                """;

        assertThat(method.invoke(service(), node, originalText)).isEqualTo(new BigDecimal("20000.00"));

        FulfillmentPlan tailNode = new FulfillmentPlan();
        tailNode.setNodeName("尾款支付");
        tailNode.setSourceClause("第七条");
        String adjacentPayments = """
                六、中期付款
                计划付款金额：人民币30,000元。
                七、尾款支付
                甲方应支付尾款人民币50,000元。
                节点名称：尾款支付。
                八、发票开具
                发票金额人民币100,000元。
                """;
        assertThat(method.invoke(service(), tailNode, adjacentPayments)).isEqualTo(new BigDecimal("50000.00"));
    }

    @Test
    void extractionUsesContractLevelGuardAndDoesNotHoldMethodTransaction() throws Exception {
        FulfillmentService service = service();
        Method begin = FulfillmentService.class.getDeclaredMethod("beginExtraction", Long.class);
        Method end = FulfillmentService.class.getDeclaredMethod("endExtraction", Long.class);
        begin.setAccessible(true);
        end.setAccessible(true);

        assertThat(begin.invoke(service, 10L)).isEqualTo(true);
        assertThat(begin.invoke(service, 10L)).isEqualTo(false);
        assertThat(begin.invoke(service, 11L)).isEqualTo(true);
        end.invoke(service, 10L);
        assertThat(begin.invoke(service, 10L)).isEqualTo(true);

        assertThat(FulfillmentService.class.getMethod("extractPlans", Long.class)
                .getAnnotation(Transactional.class)).isNull();
        assertThat(FulfillmentService.class.getMethod("initializeMemberE", Long.class)
                .getAnnotation(Transactional.class)).isNull();
    }

    @Test
    void acceptanceRequiresDeliveryConfirmationAndIsClearedWhenUnconfirmed() throws Exception {
        FulfillmentService service = service();
        Method accept = FulfillmentService.class.getDeclaredMethod(
                "applyAcceptance", FulfillmentDeliverable.class, boolean.class);
        Method confirm = FulfillmentService.class.getDeclaredMethod(
                "applyConfirm", FulfillmentDeliverable.class, boolean.class);
        accept.setAccessible(true);
        confirm.setAccessible(true);
        FulfillmentDeliverable deliverable = new FulfillmentDeliverable();
        deliverable.setConfirmed(0);

        assertThatThrownBy(() -> accept.invoke(service, deliverable, true))
                .hasCauseInstanceOf(IllegalArgumentException.class);

        confirm.invoke(service, deliverable, true);
        accept.invoke(service, deliverable, true);
        assertThat(deliverable.getAcceptancePassed()).isEqualTo(1);

        confirm.invoke(service, deliverable, false);
        assertThat(deliverable.getAcceptancePassed()).isZero();
        assertThat(deliverable.getAcceptedBy()).isNull();
        assertThat(deliverable.getAcceptedAt()).isNull();
    }

    @Test
    void deliverableWorkflowRequiresSubmitConfirmAndAcceptanceInOrder() throws Exception {
        FulfillmentService service = service();
        Method submit = FulfillmentService.class.getDeclaredMethod(
                "transitionDeliverableToSubmitted", FulfillmentDeliverable.class);
        Method confirm = FulfillmentService.class.getDeclaredMethod(
                "transitionDeliverableAccepted", FulfillmentDeliverable.class, String.class);
        Method acceptance = FulfillmentService.class.getDeclaredMethod(
                "transitionDeliverableAcceptancePassed", FulfillmentDeliverable.class, String.class);
        submit.setAccessible(true);
        confirm.setAccessible(true);
        acceptance.setAccessible(true);

        FulfillmentDeliverable deliverable = new FulfillmentDeliverable();
        deliverable.setStatus("PENDING_SUBMIT");
        deliverable.setFileId(1L);
        deliverable.setSubmissionVersion(0);

        assertThatThrownBy(() -> confirm.invoke(service, deliverable, "确认"))
                .hasCauseInstanceOf(IllegalStateException.class);

        submit.invoke(service, deliverable);
        assertThat(deliverable.getStatus()).isEqualTo("SUBMITTED");
        assertThat(deliverable.getSubmissionVersion()).isEqualTo(1);

        assertThatThrownBy(() -> acceptance.invoke(service, deliverable, "验收确认"))
                .hasCauseInstanceOf(IllegalStateException.class);

        confirm.invoke(service, deliverable, "确认");
        assertThat(deliverable.getStatus()).isEqualTo("ACCEPTED");
        assertThat(deliverable.getConfirmed()).isEqualTo(1);

        acceptance.invoke(service, deliverable, "验收确认");
        assertThat(deliverable.getStatus()).isEqualTo("ACCEPTANCE_PASSED");
        assertThat(deliverable.getAcceptancePassed()).isEqualTo(1);
    }

    @Test
    void supplementStoresReviewComment() throws Exception {
        FulfillmentService service = service();
        Method review = FulfillmentService.class.getDeclaredMethod(
                "transitionDeliverableReview", FulfillmentDeliverable.class, String.class, String.class);
        review.setAccessible(true);
        FulfillmentDeliverable deliverable = new FulfillmentDeliverable();
        deliverable.setStatus("SUBMITTED");

        review.invoke(service, deliverable, "NEED_SUPPLEMENT", "补充验收单");
        assertThat(deliverable.getStatus()).isEqualTo("NEED_SUPPLEMENT");
        assertThat(deliverable.getReviewComment()).isEqualTo("补充验收单");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reminderReceiversEscalateWithWarningLevel() throws Exception {
        Method method = FulfillmentService.class.getDeclaredMethod(
                "reminderReceivers", FulfillmentPlanVO.class, ContractMain.class);
        method.setAccessible(true);

        ContractMain contract = new ContractMain();
        contract.setDeptId(10L);

        assertThat((List<String>) method.invoke(service(), planVo("LEVEL1"), contract))
                .containsExactly("合同负责人:owner");
        assertThat((List<String>) method.invoke(service(), planVo("LEVEL2"), contract))
                .containsExactly("合同负责人:owner", "部门主管");
        assertThat((List<String>) method.invoke(service(), planVo("LEVEL3"), contract))
                .containsExactly("合同负责人:owner", "部门主管", "企业高管");
        assertThat((List<String>) method.invoke(service(), planVo("OVERDUE"), contract))
                .containsExactly("合同负责人:owner", "部门主管", "企业高管");
    }

    @Test
    void reminderContentHighlightsUrgencyAndEntry() throws Exception {
        Method method = FulfillmentService.class.getDeclaredMethod("reminderContent", FulfillmentPlanVO.class);
        method.setAccessible(true);

        assertThat(method.invoke(service(), planVo("LEVEL3")).toString())
                .contains("最后提醒")
                .contains("处理入口");
        assertThat(method.invoke(service(), planVo("OVERDUE")).toString())
                .contains("逾期通知")
                .contains("延期说明");
    }

    @Test
    void sanitizeContractTextMasksSensitiveInformation() throws Exception {
        Method method = FulfillmentService.class.getDeclaredMethod("sanitizeContractText", String.class);
        method.setAccessible(true);

        String sanitized = method.invoke(service(),
                "联系人13812345678，邮箱 test@example.com，身份证110101199001011234，金额人民币100000元").toString();

        assertThat(sanitized)
                .contains("[PHONE]", "[EMAIL]", "[ID_CARD]", "[AMOUNT]")
                .doesNotContain("13812345678", "test@example.com", "110101199001011234");
    }

    @Test
    void resolvePlannedDateParsesRelativeNaturalAndWorkdays() throws Exception {
        Method method = FulfillmentService.class.getDeclaredMethod(
                "resolvePlannedDate",
                AiDraftService.FulfillmentNode.class,
                List.class,
                ContractMain.class,
                LocalDate.class);
        method.setAccessible(true);
        ContractMain contract = new ContractMain();
        contract.setSignDate(LocalDate.of(2026, 1, 1));

        AiDraftService.FulfillmentNode delivery = new AiDraftService.FulfillmentNode(
                "交付", "DELIVERY", null, "乙方", "合同签署后45个自然日内交付", 0.91, true, false);
        assertThat(method.invoke(service(), delivery, List.of(delivery), contract, LocalDate.of(2026, 1, 3)))
                .isEqualTo(LocalDate.of(2026, 2, 15));

        AiDraftService.FulfillmentNode acceptance = new AiDraftService.FulfillmentNode(
                "验收", "ACCEPTANCE", LocalDate.of(2026, 1, 2), "甲方", "2026-01-02验收", 0.91, true, true);
        AiDraftService.FulfillmentNode payment = new AiDraftService.FulfillmentNode(
                "付款", "PAYMENT", null, "甲方", "验收通过后10个工作日内付款", 0.91, true, false);
        assertThat(method.invoke(service(), payment, List.of(acceptance, payment), contract, LocalDate.of(2026, 1, 3)))
                .isEqualTo(LocalDate.of(2026, 1, 16));
    }

    private FulfillmentPlan planDueIn(long days) {
        FulfillmentPlan plan = new FulfillmentPlan();
        plan.setStatus("TODO");
        plan.setDueDate(LocalDate.now().plusDays(days));
        plan.setConfirmStatus("CONFIRMED");
        return plan;
    }

    private FulfillmentPlanRequest request(LocalDate dueDate, String delayReason) {
        return new FulfillmentPlanRequest(
                1L,
                "Acceptance",
                "ACCEPTANCE",
                dueDate,
                "TODO",
                0,
                null,
                "owner",
                delayReason,
                ""
        );
    }

    private FulfillmentService service() {
        return new FulfillmentService(
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null,
                null, null
        );
    }

    private FulfillmentPlanVO planVo(String warningLevel) {
        return planVo(warningLevel, "NONE");
    }

    private FulfillmentPlanVO planVo(String warningLevel, String delayStatus) {
        return new FulfillmentPlanVO(
                1L,
                1L,
                "C-001",
                "Test Contract",
                "Counterparty",
                "Acceptance",
                "ACCEPTANCE",
                LocalDate.now().plusDays(7),
                "TODO",
                0,
                null,
                "owner",
                "MANUAL",
                null,
                null,
                false,
                "CONFIRMED",
                0,
                delayStatus,
                null,
                null,
                null,
                null,
                null,
                null,
                warningLevel,
                7L,
                "",
                null
        );
    }
}
