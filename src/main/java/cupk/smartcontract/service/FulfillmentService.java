package cupk.smartcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import cupk.smartcontract.entity.ContractMain;
import cupk.smartcontract.entity.FulfillmentDeliverable;
import cupk.smartcontract.entity.FulfillmentPlan;
import cupk.smartcontract.entity.PaymentPlan;
import cupk.smartcontract.entity.PaymentRecord;
import cupk.smartcontract.entity.ReminderRecord;
import cupk.smartcontract.mapper.ContractMainMapper;
import cupk.smartcontract.mapper.FulfillmentDeliverableMapper;
import cupk.smartcontract.mapper.FulfillmentPlanMapper;
import cupk.smartcontract.mapper.PaymentPlanMapper;
import cupk.smartcontract.mapper.PaymentRecordMapper;
import cupk.smartcontract.mapper.ReminderRecordMapper;
import cupk.smartcontract.security.SecurityContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FulfillmentService {
    private static final Logger log = LoggerFactory.getLogger(FulfillmentService.class);
    private static final List<String> CLOSED_STATUS = List.of("COMPLETED", "HANDLED");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal DEFAULT_PENALTY_RATE = new BigDecimal("0.05");
    private static final String RESPONSIBILITY_DISCLAIMER = "责任归属仅作辅助提示，最终由人工确认，不自动作出法律结论。";

    private final FulfillmentPlanMapper planMapper;
    private final ReminderRecordMapper reminderMapper;
    private final FulfillmentDeliverableMapper deliverableMapper;
    private final PaymentPlanMapper paymentPlanMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final ContractMainMapper contractMapper;
    private final ContractManagementService contractService;
    private final JdbcTemplate jdbcTemplate;
    private volatile boolean schemaReady;

    public FulfillmentService(FulfillmentPlanMapper planMapper,
                              ReminderRecordMapper reminderMapper,
                              FulfillmentDeliverableMapper deliverableMapper,
                              PaymentPlanMapper paymentPlanMapper,
                              PaymentRecordMapper paymentRecordMapper,
                              ContractMainMapper contractMapper,
                              ContractManagementService contractService,
                              JdbcTemplate jdbcTemplate) {
        this.planMapper = planMapper;
        this.reminderMapper = reminderMapper;
        this.deliverableMapper = deliverableMapper;
        this.paymentPlanMapper = paymentPlanMapper;
        this.paymentRecordMapper = paymentRecordMapper;
        this.contractMapper = contractMapper;
        this.contractService = contractService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureSchemaOnStartup() {
        try {
            createTables();
            schemaReady = true;
        } catch (Exception ex) {
            log.warn("Fulfillment schema initialization skipped: {}", ex.getMessage());
        }
    }

    private void ensureSchemaReady() {
        if (schemaReady) {
            return;
        }
        createTables();
        schemaReady = true;
    }

    private void createTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS fulfillment_plan (
                  plan_id bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'plan id',
                  contract_id bigint unsigned NOT NULL COMMENT 'contract id',
                  node_name varchar(120) NOT NULL COMMENT 'node name',
                  plan_type varchar(40) NOT NULL DEFAULT 'OTHER' COMMENT 'plan type',
                  due_date date NOT NULL COMMENT 'due date',
                  status varchar(40) NOT NULL DEFAULT 'TODO' COMMENT 'status',
                  progress tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'progress',
                  owner_name varchar(80) DEFAULT NULL COMMENT 'owner name',
                  source_type varchar(40) NOT NULL DEFAULT 'MANUAL' COMMENT 'source type',
                  extracted_rule varchar(200) DEFAULT NULL COMMENT 'extract rule',
                  remark varchar(500) DEFAULT NULL COMMENT 'remark',
                  handled_at datetime DEFAULT NULL COMMENT 'handled time',
                  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
                  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
                  is_deleted tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'delete flag',
                  PRIMARY KEY (plan_id),
                  KEY idx_fulfillment_contract (contract_id, due_date, status),
                  KEY idx_fulfillment_due (due_date, status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='fulfillment plan'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS reminder_record (
                  reminder_id bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'reminder id',
                  plan_id bigint unsigned NOT NULL COMMENT 'plan id',
                  contract_id bigint unsigned NOT NULL COMMENT 'contract id',
                  reminder_level varchar(40) NOT NULL COMMENT 'reminder level',
                  reminder_date date NOT NULL COMMENT 'reminder date',
                  channel varchar(40) NOT NULL DEFAULT 'IN_APP' COMMENT 'channel',
                  receiver varchar(80) DEFAULT NULL COMMENT 'receiver',
                  content varchar(500) NOT NULL COMMENT 'content',
                  send_status varchar(40) NOT NULL DEFAULT 'SENT' COMMENT 'send status',
                  sent_at datetime NOT NULL COMMENT 'sent time',
                  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
                  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
                  is_deleted tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'delete flag',
                  PRIMARY KEY (reminder_id),
                  KEY idx_reminder_plan (plan_id, reminder_level, reminder_date),
                  KEY idx_reminder_contract (contract_id, sent_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='reminder record'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS fulfillment_deliverable (
                  deliverable_id bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'deliverable id',
                  contract_id bigint unsigned NOT NULL COMMENT 'contract id',
                  deliverable_type varchar(60) NOT NULL COMMENT 'deliverable type',
                  deliverable_name varchar(120) NOT NULL COMMENT 'deliverable name',
                  stage_name varchar(80) NOT NULL COMMENT 'stage name',
                  confirm_method varchar(80) NOT NULL DEFAULT 'CHECKLIST' COMMENT 'confirm method',
                  confirmed tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'confirmed flag',
                  confirmer varchar(80) DEFAULT NULL COMMENT 'confirmer',
                  confirmed_at datetime DEFAULT NULL COMMENT 'confirmed time',
                  remark varchar(500) DEFAULT NULL COMMENT 'remark',
                  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
                  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
                  is_deleted tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'delete flag',
                  PRIMARY KEY (deliverable_id),
                  KEY idx_deliverable_contract (contract_id, deliverable_type, confirmed)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='fulfillment deliverable'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS payment_plan (
                  payment_plan_id bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'payment plan id',
                  contract_id bigint unsigned NOT NULL COMMENT 'contract id',
                  phase_name varchar(120) NOT NULL COMMENT 'phase name',
                  percentage decimal(8,2) NOT NULL DEFAULT 0.00 COMMENT 'payment percentage',
                  planned_amount decimal(15,2) NOT NULL DEFAULT 0.00 COMMENT 'planned amount',
                  due_date date NOT NULL COMMENT 'due date',
                  prerequisite_delivery varchar(300) DEFAULT NULL COMMENT 'prerequisite delivery',
                  penalty_rate decimal(8,4) NOT NULL DEFAULT 0.0500 COMMENT 'daily penalty rate percent',
                  status varchar(40) NOT NULL DEFAULT 'UNPAID' COMMENT 'status',
                  remark varchar(500) DEFAULT NULL COMMENT 'remark',
                  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
                  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
                  is_deleted tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'delete flag',
                  PRIMARY KEY (payment_plan_id),
                  KEY idx_payment_plan_contract (contract_id, due_date, status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='payment plan'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS payment_record (
                  record_id bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'payment record id',
                  payment_plan_id bigint unsigned NOT NULL COMMENT 'payment plan id',
                  contract_id bigint unsigned NOT NULL COMMENT 'contract id',
                  paid_amount decimal(15,2) NOT NULL DEFAULT 0.00 COMMENT 'paid amount',
                  paid_date date NOT NULL COMMENT 'paid date',
                  payer varchar(120) DEFAULT NULL COMMENT 'payer',
                  receiver varchar(120) DEFAULT NULL COMMENT 'receiver',
                  remark varchar(500) DEFAULT NULL COMMENT 'remark',
                  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
                  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
                  is_deleted tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'delete flag',
                  PRIMARY KEY (record_id),
                  KEY idx_payment_record_plan (payment_plan_id, paid_date),
                  KEY idx_payment_record_contract (contract_id, paid_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='payment record'
                """);
        ensureLegacyColumns();
        ensureMemberEColumns();
    }

    private void ensureLegacyColumns() {
        ensureColumn("fulfillment_plan", "node_name", "`node_name` varchar(120) NOT NULL DEFAULT '' COMMENT 'node name'");
        ensureColumn("fulfillment_plan", "plan_type", "`plan_type` varchar(40) NOT NULL DEFAULT 'OTHER' COMMENT 'plan type'");
        ensureColumn("fulfillment_plan", "progress", "`progress` tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'progress'");
        ensureColumn("fulfillment_plan", "owner_name", "`owner_name` varchar(80) DEFAULT NULL COMMENT 'owner name'");
        ensureColumn("fulfillment_plan", "source_type", "`source_type` varchar(40) NOT NULL DEFAULT 'MANUAL' COMMENT 'source type'");
        ensureColumn("fulfillment_plan", "extracted_rule", "`extracted_rule` varchar(200) DEFAULT NULL COMMENT 'extract rule'");
        ensureColumn("fulfillment_plan", "remark", "`remark` varchar(500) DEFAULT NULL COMMENT 'remark'");
        ensureColumn("fulfillment_plan", "handled_at", "`handled_at` datetime DEFAULT NULL COMMENT 'handled time'");
        modifyColumnIfExists("fulfillment_plan", "milestone_name", "`milestone_name` varchar(150) NOT NULL DEFAULT '' COMMENT 'legacy milestone name'");
        modifyColumnIfExists("fulfillment_plan", "owner_id", "`owner_id` bigint unsigned NOT NULL DEFAULT 1 COMMENT 'legacy owner id'");

        ensureColumn("reminder_record", "plan_id", "`plan_id` bigint unsigned NOT NULL DEFAULT 0 COMMENT 'plan id'");
        ensureColumn("reminder_record", "reminder_level", "`reminder_level` varchar(40) NOT NULL DEFAULT 'LEVEL1' COMMENT 'reminder level'");
        ensureColumn("reminder_record", "reminder_date", "`reminder_date` date DEFAULT NULL COMMENT 'reminder date'");
        ensureColumn("reminder_record", "channel", "`channel` varchar(40) NOT NULL DEFAULT 'IN_APP' COMMENT 'channel'");
        ensureColumn("reminder_record", "receiver", "`receiver` varchar(80) DEFAULT NULL COMMENT 'receiver'");
        ensureColumn("reminder_record", "content", "`content` varchar(500) NOT NULL DEFAULT '' COMMENT 'content'");
        ensureColumn("reminder_record", "sent_at", "`sent_at` datetime DEFAULT NULL COMMENT 'sent time'");
        ensureColumn("reminder_record", "created_at", "`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time'");
        ensureColumn("reminder_record", "updated_at", "`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time'");
        ensureColumn("reminder_record", "is_deleted", "`is_deleted` tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'delete flag'");
        modifyColumnIfExists("reminder_record", "reminder_type", "`reminder_type` varchar(50) NOT NULL DEFAULT '' COMMENT 'legacy reminder type'");
        modifyColumnIfExists("reminder_record", "send_time", "`send_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'legacy send time'");
        modifyColumnIfExists("reminder_record", "receiver_id", "`receiver_id` bigint unsigned NOT NULL DEFAULT 1 COMMENT 'legacy receiver id'");
    }

    private void ensureMemberEColumns() {
        ensureColumn("fulfillment_deliverable", "stage_name", "`stage_name` varchar(80) NOT NULL DEFAULT '' COMMENT 'stage name'");
        ensureColumn("fulfillment_deliverable", "confirm_method", "`confirm_method` varchar(80) NOT NULL DEFAULT '逐项勾选确认' COMMENT 'confirm method'");
        ensureColumn("fulfillment_deliverable", "confirmed", "`confirmed` tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'confirmed flag'");
        ensureColumn("fulfillment_deliverable", "confirmer", "`confirmer` varchar(80) DEFAULT NULL COMMENT 'confirmer'");
        modifyColumnIfExists("fulfillment_deliverable", "deliverable_name", "`deliverable_name` varchar(200) NOT NULL DEFAULT '' COMMENT 'deliverable name'");
        modifyColumnIfExists("fulfillment_deliverable", "deliverable_type", "`deliverable_type` varchar(60) NOT NULL DEFAULT '' COMMENT 'deliverable type'");
        modifyColumnIfExists("fulfillment_deliverable", "contract_stage", "`contract_stage` varchar(50) NOT NULL DEFAULT '' COMMENT 'legacy contract stage'");

        ensureColumn("payment_plan", "phase_name", "`phase_name` varchar(120) NOT NULL DEFAULT '' COMMENT 'phase name'");
        ensureColumn("payment_plan", "percentage", "`percentage` decimal(8,2) NOT NULL DEFAULT 0.00 COMMENT 'payment percentage'");
        ensureColumn("payment_plan", "planned_amount", "`planned_amount` decimal(15,2) NOT NULL DEFAULT 0.00 COMMENT 'planned amount'");
        ensureColumn("payment_plan", "prerequisite_delivery", "`prerequisite_delivery` varchar(300) DEFAULT NULL COMMENT 'prerequisite delivery'");
        ensureColumn("payment_plan", "penalty_rate", "`penalty_rate` decimal(8,4) NOT NULL DEFAULT 0.0500 COMMENT 'daily penalty rate percent'");
        ensureColumn("payment_plan", "remark", "`remark` varchar(500) DEFAULT NULL COMMENT 'remark'");
        modifyColumnIfExists("payment_plan", "plan_id", "`plan_id` bigint unsigned NOT NULL DEFAULT 1 COMMENT 'legacy plan id'");
        modifyColumnIfExists("payment_plan", "installment_no", "`installment_no` int NOT NULL DEFAULT 1 COMMENT 'legacy installment no'");
        modifyColumnIfExists("payment_plan", "ratio", "`ratio` decimal(5,4) NOT NULL DEFAULT 0.0000 COMMENT 'legacy ratio'");
        modifyColumnIfExists("payment_plan", "amount", "`amount` decimal(15,2) NOT NULL DEFAULT 0.00 COMMENT 'legacy amount'");

        ensureColumn("payment_record", "paid_date", "`paid_date` date DEFAULT NULL COMMENT 'paid date'");
        ensureColumn("payment_record", "payer", "`payer` varchar(120) DEFAULT NULL COMMENT 'payer'");
        ensureColumn("payment_record", "receiver", "`receiver` varchar(120) DEFAULT NULL COMMENT 'receiver'");
        ensureColumn("payment_record", "remark", "`remark` varchar(500) DEFAULT NULL COMMENT 'remark'");
        modifyColumnIfExists("payment_record", "paid_amount", "`paid_amount` decimal(15,2) NOT NULL DEFAULT 0.00 COMMENT 'paid amount'");
        modifyColumnIfExists("payment_record", "paid_at", "`paid_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'legacy paid time'");
    }

    private void ensureColumn(String tableName, String columnName, String columnDefinition) {
        if (!hasColumn(tableName, columnName)) {
            jdbcTemplate.execute("ALTER TABLE `" + tableName + "` ADD COLUMN " + columnDefinition);
        }
    }

    private void modifyColumnIfExists(String tableName, String columnName, String columnDefinition) {
        if (hasColumn(tableName, columnName)) {
            jdbcTemplate.execute("ALTER TABLE `" + tableName + "` MODIFY COLUMN " + columnDefinition);
        }
    }

    private boolean hasColumn(String tableName, String columnName) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, Long.class, tableName, columnName);
        return count != null && count > 0;
    }

    public List<FulfillmentPlanVO> listPlans(Long contractId, String status, String keyword) {
        ensureSchemaReady();
        syncOverdueStatus();
        LambdaQueryWrapper<FulfillmentPlan> wrapper = new LambdaQueryWrapper<FulfillmentPlan>()
                .eq(contractId != null, FulfillmentPlan::getContractId, contractId)
                .and(StringUtils.hasText(keyword), w -> w.like(FulfillmentPlan::getNodeName, keyword)
                        .or().like(FulfillmentPlan::getOwnerName, keyword)
                        .or().like(FulfillmentPlan::getRemark, keyword))
                .orderByAsc(FulfillmentPlan::getDueDate)
                .orderByDesc(FulfillmentPlan::getUpdatedAt);
        if (StringUtils.hasText(status) && !"WARNING".equals(status)) {
            wrapper.eq(FulfillmentPlan::getStatus, status);
        }
        List<FulfillmentPlanVO> plans = toPlanVos(planMapper.selectList(wrapper));
        if ("WARNING".equals(status)) {
            plans = plans.stream()
                    .filter(plan -> plan.warningLevel() != null
                            && (plan.warningLevel().startsWith("LEVEL") || "OVERDUE".equals(plan.warningLevel())))
                    .toList();
        }
        return plans;
    }

    @Transactional
    public List<FulfillmentPlanVO> extractPlans(Long contractId) {
        ensureSchemaReady();
        ContractMain contract = contractService.findContract(contractId);
        contractService.assertCanAccess(contractId);
        List<FulfillmentPlan> existing = planMapper.selectList(new LambdaQueryWrapper<FulfillmentPlan>()
                .eq(FulfillmentPlan::getContractId, contractId)
                .eq(FulfillmentPlan::getSourceType, "AUTO"));
        if (!existing.isEmpty()) {
            return listPlans(contractId, null, null);
        }

        LocalDate baseDate = contract.getDueDate() != null ? contract.getDueDate() : LocalDate.now().plusDays(45);
        List<FulfillmentPlan> plans = List.of(
                autoPlan(contractId, "履约材料准备", "PREPARE", baseDate.minusDays(30), "按合同到期日前30天生成"),
                autoPlan(contractId, "履约进度确认", "CHECK", baseDate.minusDays(7), "按合同到期日前7天生成"),
                autoPlan(contractId, "最终履约验收", "ACCEPTANCE", baseDate, "按合同到期日生成")
        );
        plans.forEach(planMapper::insert);
        syncOverdueStatus();
        return listPlans(contractId, null, null);
    }

    @Transactional
    public void initializeMemberE(Long contractId) {
        ensureSchemaReady();
        ContractMain contract = contractService.findContract(contractId);
        contractService.assertCanAccess(contractId);
        extractPlans(contractId);
        createStandardDeliverables(contractId);
        createStandardPaymentPlans(contract);
    }

    @Transactional
    public FulfillmentPlanVO createPlan(FulfillmentPlanRequest request) {
        ensureSchemaReady();
        if (request == null || request.contractId() == null) {
            throw new IllegalArgumentException("contractId is required");
        }
        contractService.assertCanAccess(request.contractId());
        FulfillmentPlan plan = new FulfillmentPlan();
        applyPlanRequest(plan, request);
        plan.setSourceType("MANUAL");
        plan.setExtractedRule("手动维护");
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        plan.setDeleted(0);
        planMapper.insert(plan);
        return toPlanVo(planMapper.selectById(plan.getPlanId()));
    }

    @Transactional
    public FulfillmentPlanVO updatePlan(Long planId, FulfillmentPlanRequest request) {
        ensureSchemaReady();
        FulfillmentPlan plan = assertPlanAccess(planId);
        applyPlanRequest(plan, request);
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        syncOverdueStatus();
        return toPlanVo(planMapper.selectById(planId));
    }

    @Transactional
    public FulfillmentPlanVO handleOverdue(Long planId) {
        ensureSchemaReady();
        FulfillmentPlan plan = assertPlanAccess(planId);
        plan.setStatus("HANDLED");
        plan.setHandledAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        return toPlanVo(planMapper.selectById(planId));
    }

    @Transactional
    public List<ReminderRecordVO> dispatchReminders(Long contractId) {
        ensureSchemaReady();
        if (contractId != null) {
            contractService.assertCanAccess(contractId);
        }
        syncOverdueStatus();
        List<FulfillmentPlanVO> warningPlans = listPlans(contractId, "WARNING", null);
        LocalDate today = LocalDate.now();
        for (FulfillmentPlanVO vo : warningPlans) {
            if ("NORMAL".equals(vo.warningLevel()) || "NONE".equals(vo.warningLevel())) {
                continue;
            }
            Long count = reminderMapper.selectCount(new LambdaQueryWrapper<ReminderRecord>()
                    .eq(ReminderRecord::getPlanId, vo.planId())
                    .eq(ReminderRecord::getReminderLevel, vo.warningLevel())
                    .eq(ReminderRecord::getReminderDate, today));
            if (count != null && count > 0) {
                continue;
            }
            ReminderRecord record = new ReminderRecord();
            record.setPlanId(vo.planId());
            record.setContractId(vo.contractId());
            record.setReminderLevel(vo.warningLevel());
            record.setReminderDate(today);
            record.setChannel("IN_APP");
            record.setReceiver(StringUtils.hasText(vo.ownerName()) ? vo.ownerName() : SecurityContext.username());
            record.setContent(reminderContent(vo));
            record.setSendStatus("SENT");
            record.setSentAt(LocalDateTime.now());
            record.setCreatedAt(LocalDateTime.now());
            record.setUpdatedAt(LocalDateTime.now());
            record.setDeleted(0);
            reminderMapper.insert(record);
        }
        return listReminders(contractId);
    }

    public List<ReminderRecordVO> listReminders(Long contractId) {
        ensureSchemaReady();
        if (contractId != null) {
            contractService.assertCanAccess(contractId);
        }
        List<ReminderRecord> records = reminderMapper.selectList(new LambdaQueryWrapper<ReminderRecord>()
                .eq(contractId != null, ReminderRecord::getContractId, contractId)
                .orderByDesc(ReminderRecord::getSentAt)
                .last("LIMIT 50"));
        return toReminderVos(records);
    }

    public FulfillmentStats stats(Long contractId) {
        ensureSchemaReady();
        if (contractId != null) {
            contractService.assertCanAccess(contractId);
        }
        List<FulfillmentPlanVO> plans = listPlans(contractId, null, null);
        long total = plans.size();
        long warning = plans.stream().filter(p -> p.warningLevel() != null && p.warningLevel().startsWith("LEVEL")).count();
        long overdue = plans.stream().filter(p -> "OVERDUE".equals(p.warningLevel()) || "OVERDUE".equals(p.status())).count();
        long completed = plans.stream().filter(p -> "COMPLETED".equals(p.status())).count();
        long handled = plans.stream().filter(p -> "HANDLED".equals(p.status())).count();
        long reminderCount = listReminders(contractId).size();
        return new FulfillmentStats(total, warning, overdue, completed, handled, reminderCount);
    }

    public List<DeliverableVO> listDeliverables(Long contractId) {
        ensureSchemaReady();
        LambdaQueryWrapper<FulfillmentDeliverable> wrapper = new LambdaQueryWrapper<FulfillmentDeliverable>()
                .eq(contractId != null, FulfillmentDeliverable::getContractId, contractId)
                .orderByAsc(FulfillmentDeliverable::getDeliverableId);
        return toDeliverableVos(deliverableMapper.selectList(wrapper));
    }

    @Transactional
    public DeliverableVO updateDeliverable(Long deliverableId, DeliverableRequest request) {
        ensureSchemaReady();
        FulfillmentDeliverable item = assertDeliverableAccess(deliverableId);
        if (StringUtils.hasText(request.deliverableType())) {
            item.setDeliverableType(request.deliverableType());
        }
        if (StringUtils.hasText(request.deliverableName())) {
            item.setDeliverableName(request.deliverableName());
        }
        if (StringUtils.hasText(request.stageName())) {
            item.setStageName(request.stageName());
        }
        item.setRemark(StringUtils.hasText(request.remark()) ? request.remark() : "");
        if (request.confirmed() != null) {
            applyConfirm(item, request.confirmed());
        }
        item.setUpdatedAt(LocalDateTime.now());
        deliverableMapper.updateById(item);
        return toDeliverableVo(deliverableMapper.selectById(deliverableId));
    }

    @Transactional
    public DeliverableVO confirmDeliverable(Long deliverableId, boolean confirmed) {
        ensureSchemaReady();
        FulfillmentDeliverable item = assertDeliverableAccess(deliverableId);
        applyConfirm(item, confirmed);
        item.setUpdatedAt(LocalDateTime.now());
        deliverableMapper.updateById(item);
        return toDeliverableVo(deliverableMapper.selectById(deliverableId));
    }

    public List<PaymentPlanVO> listPaymentPlans(Long contractId) {
        ensureSchemaReady();
        LambdaQueryWrapper<PaymentPlan> wrapper = new LambdaQueryWrapper<PaymentPlan>()
                .eq(contractId != null, PaymentPlan::getContractId, contractId)
                .orderByAsc(PaymentPlan::getDueDate)
                .orderByAsc(PaymentPlan::getPaymentPlanId);
        return toPaymentPlanVos(paymentPlanMapper.selectList(wrapper));
    }

    @Transactional
    public PaymentPlanVO createPaymentPlan(PaymentPlanRequest request) {
        ensureSchemaReady();
        if (request == null || request.contractId() == null) {
            throw new IllegalArgumentException("contractId is required");
        }
        contractService.assertCanAccess(request.contractId());
        PaymentPlan plan = new PaymentPlan();
        applyPaymentPlanRequest(plan, request);
        validatePaymentPercentage(plan);
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        plan.setDeleted(0);
        paymentPlanMapper.insert(plan);
        syncPaymentPlanStatus(plan);
        return toPaymentPlanVo(paymentPlanMapper.selectById(plan.getPaymentPlanId()));
    }

    @Transactional
    public PaymentPlanVO updatePaymentPlan(Long paymentPlanId, PaymentPlanRequest request) {
        ensureSchemaReady();
        PaymentPlan plan = assertPaymentPlanAccess(paymentPlanId);
        applyPaymentPlanRequest(plan, request);
        validatePaymentPercentage(plan);
        plan.setUpdatedAt(LocalDateTime.now());
        paymentPlanMapper.updateById(plan);
        syncPaymentPlanStatus(plan);
        return toPaymentPlanVo(paymentPlanMapper.selectById(paymentPlanId));
    }

    public List<PaymentRecordVO> listPaymentRecords(Long contractId) {
        ensureSchemaReady();
        LambdaQueryWrapper<PaymentRecord> wrapper = new LambdaQueryWrapper<PaymentRecord>()
                .eq(contractId != null, PaymentRecord::getContractId, contractId)
                .orderByDesc(PaymentRecord::getPaidDate)
                .orderByDesc(PaymentRecord::getPaymentRecordId);
        return toPaymentRecordVos(paymentRecordMapper.selectList(wrapper));
    }

    @Transactional
    public PaymentRecordVO createPaymentRecord(Long paymentPlanId, PaymentRecordRequest request) {
        ensureSchemaReady();
        PaymentPlan plan = assertPaymentPlanAccess(paymentPlanId);
        BigDecimal paidAmount = money(request.paidAmount());
        BigDecimal remainingAmount = money(plan.getPlannedAmount()).subtract(paidAmount(plan.getPaymentPlanId()))
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("该付款计划已足额到账，无需重复登记");
        }
        if (paidAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("到账金额必须大于 0");
        }
        if (paidAmount.compareTo(remainingAmount) > 0) {
            throw new IllegalArgumentException("到账金额不能超过剩余未收金额 " + remainingAmount + " 元");
        }
        PaymentRecord record = new PaymentRecord();
        record.setPaymentPlanId(plan.getPaymentPlanId());
        record.setContractId(plan.getContractId());
        record.setPaidAmount(paidAmount);
        record.setPaidDate(request.paidDate() != null ? request.paidDate() : LocalDate.now());
        record.setPayer(StringUtils.hasText(request.payer()) ? request.payer().trim() : "甲方");
        record.setReceiver(StringUtils.hasText(request.receiver()) ? request.receiver().trim() : "乙方");
        record.setRemark(StringUtils.hasText(request.remark()) ? request.remark().trim() : "");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        record.setDeleted(0);
        paymentRecordMapper.insert(record);
        syncPaymentPlanStatus(plan);
        return toPaymentRecordVo(paymentRecordMapper.selectById(record.getPaymentRecordId()));
    }

    @Transactional
    public void deletePaymentRecord(Long recordId) {
        ensureSchemaReady();
        PaymentRecord record = paymentRecordMapper.selectById(recordId);
        if (record == null) {
            throw new IllegalArgumentException("payment record not found");
        }
        contractService.assertCanAccess(record.getContractId());
        PaymentPlan plan = paymentPlanMapper.selectById(record.getPaymentPlanId());
        paymentRecordMapper.deleteById(recordId);
        if (plan != null) {
            syncPaymentPlanStatus(plan);
        }
    }

    private void createStandardDeliverables(Long contractId) {
        List<FulfillmentDeliverable> items = List.of(
                deliverable(contractId, "DESIGN_DOC", "需求设计文档", "签订阶段"),
                deliverable(contractId, "SOURCE_CODE", "源代码", "中期交付"),
                deliverable(contractId, "RUNNABLE_APP", "可运行程序", "中期交付"),
                deliverable(contractId, "ACCEPTANCE_REPORT", "验收报告", "验收阶段")
        );
        for (FulfillmentDeliverable item : items) {
            Long count = deliverableMapper.selectCount(new LambdaQueryWrapper<FulfillmentDeliverable>()
                    .eq(FulfillmentDeliverable::getContractId, contractId)
                    .eq(FulfillmentDeliverable::getDeliverableName, item.getDeliverableName()));
            if (count == null || count == 0) {
                deliverableMapper.insert(item);
            }
        }
    }

    private void createStandardPaymentPlans(ContractMain contract) {
        BigDecimal amount = money(contract.getAmount());
        LocalDate base = contract.getDueDate() != null ? contract.getDueDate() : LocalDate.now().plusDays(45);
        List<PaymentPlan> plans = List.of(
                paymentPlan(contract.getContractId(), "首期款 30%", new BigDecimal("30"), amount, base.minusDays(30), "需求设计文档"),
                paymentPlan(contract.getContractId(), "中期款 30%", new BigDecimal("30"), amount, base.minusDays(7), "源代码、可运行程序"),
                paymentPlan(contract.getContractId(), "尾款 40%", new BigDecimal("40"), amount, base, "验收报告")
        );
        for (PaymentPlan plan : plans) {
            Long count = paymentPlanMapper.selectCount(new LambdaQueryWrapper<PaymentPlan>()
                    .eq(PaymentPlan::getContractId, plan.getContractId())
                    .eq(PaymentPlan::getPhaseName, plan.getPhaseName()));
            if (count == null || count == 0) {
                validatePaymentPercentage(plan);
                paymentPlanMapper.insert(plan);
            }
        }
    }

    private FulfillmentDeliverable deliverable(Long contractId, String type, String name, String stage) {
        FulfillmentDeliverable item = new FulfillmentDeliverable();
        item.setContractId(contractId);
        item.setDeliverableType(type);
        item.setDeliverableName(name);
        item.setStageName(stage);
        item.setConfirmMethod("逐项勾选确认");
        item.setConfirmed(0);
        item.setRemark("");
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        item.setDeleted(0);
        return item;
    }

    private PaymentPlan paymentPlan(Long contractId, String phaseName, BigDecimal percentage,
                                    BigDecimal contractAmount, LocalDate dueDate, String prerequisite) {
        PaymentPlan plan = new PaymentPlan();
        plan.setContractId(contractId);
        plan.setPhaseName(phaseName);
        plan.setPercentage(percentage);
        plan.setPlannedAmount(money(contractAmount.multiply(percentage).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP)));
        plan.setDueDate(dueDate);
        plan.setPrerequisiteDelivery(prerequisite);
        plan.setPenaltyRate(DEFAULT_PENALTY_RATE);
        plan.setStatus("UNPAID");
        plan.setRemark("");
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        plan.setDeleted(0);
        return plan;
    }

    private FulfillmentPlan autoPlan(Long contractId, String name, String type, LocalDate dueDate, String rule) {
        FulfillmentPlan plan = new FulfillmentPlan();
        plan.setContractId(contractId);
        plan.setNodeName(name);
        plan.setPlanType(type);
        plan.setDueDate(dueDate);
        plan.setStatus("TODO");
        plan.setProgress(0);
        plan.setOwnerName(StringUtils.hasText(SecurityContext.username()) ? SecurityContext.username() : "合同负责人");
        plan.setSourceType("AUTO");
        plan.setExtractedRule(rule);
        plan.setRemark("");
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        plan.setDeleted(0);
        return plan;
    }

    private void applyPlanRequest(FulfillmentPlan plan, FulfillmentPlanRequest request) {
        if (request.contractId() != null) {
            contractService.assertCanAccess(request.contractId());
            plan.setContractId(request.contractId());
        }
        if (StringUtils.hasText(request.nodeName())) {
            plan.setNodeName(request.nodeName().trim());
        }
        plan.setPlanType(StringUtils.hasText(request.planType()) ? request.planType().trim() : "OTHER");
        if (request.dueDate() != null) {
            plan.setDueDate(request.dueDate());
        }
        int progress = request.progress() != null ? Math.max(0, Math.min(100, request.progress())) : 0;
        plan.setProgress(progress);
        String status = StringUtils.hasText(request.status()) ? request.status().trim() : (progress >= 100 ? "COMPLETED" : "TODO");
        if (progress >= 100) {
            status = "COMPLETED";
        } else if ("COMPLETED".equals(status)) {
            plan.setProgress(100);
        }
        plan.setStatus(status);
        plan.setOwnerName(StringUtils.hasText(request.ownerName()) ? request.ownerName().trim() : SecurityContext.username());
        plan.setRemark(StringUtils.hasText(request.remark()) ? request.remark().trim() : "");
    }

    private void applyPaymentPlanRequest(PaymentPlan plan, PaymentPlanRequest request) {
        if (request.contractId() != null) {
            contractService.assertCanAccess(request.contractId());
            plan.setContractId(request.contractId());
        }
        plan.setPhaseName(StringUtils.hasText(request.phaseName()) ? request.phaseName().trim() : "付款节点");
        plan.setPercentage(request.percentage() != null ? request.percentage() : BigDecimal.ZERO);
        plan.setPlannedAmount(money(request.plannedAmount()));
        plan.setDueDate(request.dueDate() != null ? request.dueDate() : LocalDate.now());
        plan.setPrerequisiteDelivery(StringUtils.hasText(request.prerequisiteDelivery()) ? request.prerequisiteDelivery().trim() : "");
        plan.setPenaltyRate(request.penaltyRate() != null ? request.penaltyRate() : DEFAULT_PENALTY_RATE);
        if (StringUtils.hasText(request.status())) {
            plan.setStatus(request.status().trim());
        } else if (!StringUtils.hasText(plan.getStatus())) {
            plan.setStatus("UNPAID");
        }
        plan.setRemark(StringUtils.hasText(request.remark()) ? request.remark().trim() : "");
    }

    private void validatePaymentPercentage(PaymentPlan plan) {
        BigDecimal percentage = plan.getPercentage() != null ? plan.getPercentage() : BigDecimal.ZERO;
        if (percentage.compareTo(BigDecimal.ZERO) < 0 || percentage.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException("付款比例必须在 0% 到 100% 之间");
        }
        BigDecimal total = paymentPercentageTotal(plan.getContractId(), plan.getPaymentPlanId()).add(percentage);
        if (total.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException("同一合同付款比例合计不能超过 100%，当前保存后合计为 " + formatPercent(total));
        }
    }

    private BigDecimal paymentPercentageTotal(Long contractId, Long excludingPaymentPlanId) {
        if (contractId == null) {
            return BigDecimal.ZERO;
        }
        return paymentPlanMapper.selectList(new LambdaQueryWrapper<PaymentPlan>()
                        .eq(PaymentPlan::getContractId, contractId))
                .stream()
                .filter(item -> excludingPaymentPlanId == null || !Objects.equals(item.getPaymentPlanId(), excludingPaymentPlanId))
                .map(PaymentPlan::getPercentage)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void applyConfirm(FulfillmentDeliverable item, boolean confirmed) {
        item.setConfirmed(confirmed ? 1 : 0);
        item.setConfirmer(confirmed ? SecurityContext.username() : null);
        item.setConfirmedAt(confirmed ? LocalDateTime.now() : null);
    }

    private void syncOverdueStatus() {
        List<FulfillmentPlan> overduePlans = planMapper.selectList(new LambdaQueryWrapper<FulfillmentPlan>()
                .lt(FulfillmentPlan::getDueDate, LocalDate.now())
                .notIn(FulfillmentPlan::getStatus, CLOSED_STATUS)
                .ne(FulfillmentPlan::getStatus, "OVERDUE"));
        for (FulfillmentPlan plan : overduePlans) {
            plan.setStatus("OVERDUE");
            plan.setUpdatedAt(LocalDateTime.now());
            planMapper.updateById(plan);
        }

        List<FulfillmentPlan> recoveredPlans = planMapper.selectList(new LambdaQueryWrapper<FulfillmentPlan>()
                .ge(FulfillmentPlan::getDueDate, LocalDate.now())
                .eq(FulfillmentPlan::getStatus, "OVERDUE"));
        for (FulfillmentPlan plan : recoveredPlans) {
            plan.setStatus(plan.getProgress() != null && plan.getProgress() > 0 ? "IN_PROGRESS" : "TODO");
            plan.setUpdatedAt(LocalDateTime.now());
            planMapper.updateById(plan);
        }
    }

    private void syncPaymentPlanStatus(PaymentPlan plan) {
        PaymentPlanVO vo = toPaymentPlanVo(plan);
        plan.setStatus(vo.status());
        plan.setUpdatedAt(LocalDateTime.now());
        paymentPlanMapper.updateById(plan);
    }

    private FulfillmentPlan assertPlanAccess(Long planId) {
        FulfillmentPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new IllegalArgumentException("plan not found");
        }
        contractService.assertCanAccess(plan.getContractId());
        return plan;
    }

    private FulfillmentDeliverable assertDeliverableAccess(Long deliverableId) {
        FulfillmentDeliverable item = deliverableMapper.selectById(deliverableId);
        if (item == null) {
            throw new IllegalArgumentException("deliverable not found");
        }
        contractService.assertCanAccess(item.getContractId());
        return item;
    }

    private PaymentPlan assertPaymentPlanAccess(Long paymentPlanId) {
        PaymentPlan plan = paymentPlanMapper.selectById(paymentPlanId);
        if (plan == null) {
            throw new IllegalArgumentException("payment plan not found");
        }
        contractService.assertCanAccess(plan.getContractId());
        return plan;
    }

    private List<FulfillmentPlanVO> toPlanVos(List<FulfillmentPlan> plans) {
        Map<Long, ContractMain> contracts = contractMap(plans.stream().map(FulfillmentPlan::getContractId).toList());
        return plans.stream()
                .filter(plan -> contractService.canAccess(contracts.get(plan.getContractId())))
                .map(plan -> toPlanVo(plan, contracts.get(plan.getContractId())))
                .toList();
    }

    private FulfillmentPlanVO toPlanVo(FulfillmentPlan plan) {
        return toPlanVo(plan, contractMapper.selectById(plan.getContractId()));
    }

    private FulfillmentPlanVO toPlanVo(FulfillmentPlan plan, ContractMain contract) {
        Long daysLeft = plan.getDueDate() == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), plan.getDueDate());
        return new FulfillmentPlanVO(
                plan.getPlanId(),
                plan.getContractId(),
                contract != null ? contract.getContractNo() : "",
                contract != null ? contract.getTitle() : "",
                contract != null ? contract.getCounterparty() : "",
                plan.getNodeName(),
                plan.getPlanType(),
                plan.getDueDate(),
                plan.getStatus(),
                plan.getProgress(),
                plan.getOwnerName(),
                plan.getSourceType(),
                warningLevel(plan),
                daysLeft,
                plan.getRemark(),
                plan.getUpdatedAt()
        );
    }

    private List<DeliverableVO> toDeliverableVos(List<FulfillmentDeliverable> items) {
        Map<Long, ContractMain> contracts = contractMap(items.stream().map(FulfillmentDeliverable::getContractId).toList());
        return items.stream()
                .filter(item -> contractService.canAccess(contracts.get(item.getContractId())))
                .map(item -> toDeliverableVo(item, contracts.get(item.getContractId())))
                .toList();
    }

    private DeliverableVO toDeliverableVo(FulfillmentDeliverable item) {
        return toDeliverableVo(item, contractMapper.selectById(item.getContractId()));
    }

    private DeliverableVO toDeliverableVo(FulfillmentDeliverable item, ContractMain contract) {
        return new DeliverableVO(
                item.getDeliverableId(),
                item.getContractId(),
                contract != null ? contract.getTitle() : "",
                item.getDeliverableType(),
                item.getDeliverableName(),
                item.getStageName(),
                item.getConfirmMethod(),
                item.getConfirmed() != null && item.getConfirmed() == 1,
                item.getConfirmer(),
                item.getConfirmedAt(),
                item.getRemark()
        );
    }

    private List<PaymentPlanVO> toPaymentPlanVos(List<PaymentPlan> plans) {
        Map<Long, ContractMain> contracts = contractMap(plans.stream().map(PaymentPlan::getContractId).toList());
        return plans.stream()
                .filter(plan -> contractService.canAccess(contracts.get(plan.getContractId())))
                .map(plan -> toPaymentPlanVo(plan, contracts.get(plan.getContractId())))
                .toList();
    }

    private PaymentPlanVO toPaymentPlanVo(PaymentPlan plan) {
        return toPaymentPlanVo(plan, contractMapper.selectById(plan.getContractId()));
    }

    private PaymentPlanVO toPaymentPlanVo(PaymentPlan plan, ContractMain contract) {
        BigDecimal planned = money(plan.getPlannedAmount());
        BigDecimal paid = paidAmount(plan.getPaymentPlanId());
        BigDecimal unpaid = planned.subtract(paid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        boolean prereqCompleted = prerequisiteCompleted(plan);
        long overdueDays = plan.getDueDate() != null && plan.getDueDate().isBefore(LocalDate.now()) && unpaid.compareTo(BigDecimal.ZERO) > 0
                ? ChronoUnit.DAYS.between(plan.getDueDate(), LocalDate.now())
                : 0L;
        BigDecimal rate = plan.getPenaltyRate() != null ? plan.getPenaltyRate() : DEFAULT_PENALTY_RATE;
        BigDecimal penaltyAmount = overdueDays <= 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : unpaid.multiply(rate).multiply(BigDecimal.valueOf(overdueDays)).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
        String status = paymentStatus(planned, paid, overdueDays);
        return new PaymentPlanVO(
                plan.getPaymentPlanId(),
                plan.getContractId(),
                contract != null ? contract.getTitle() : "",
                plan.getPhaseName(),
                plan.getPercentage(),
                planned,
                paid,
                unpaid,
                plan.getDueDate(),
                plan.getPrerequisiteDelivery(),
                prereqCompleted,
                rate,
                overdueDays,
                penaltyAmount,
                status,
                responsibilityHint(status, overdueDays, prereqCompleted),
                plan.getRemark()
        );
    }

    private List<PaymentRecordVO> toPaymentRecordVos(List<PaymentRecord> records) {
        Map<Long, ContractMain> contracts = contractMap(records.stream().map(PaymentRecord::getContractId).toList());
        Map<Long, PaymentPlan> plans = paymentPlanMap(records.stream().map(PaymentRecord::getPaymentPlanId).toList());
        return records.stream()
                .filter(record -> contractService.canAccess(contracts.get(record.getContractId())))
                .map(record -> toPaymentRecordVo(record, contracts.get(record.getContractId()), plans.get(record.getPaymentPlanId())))
                .toList();
    }

    private PaymentRecordVO toPaymentRecordVo(PaymentRecord record) {
        return toPaymentRecordVo(record, contractMapper.selectById(record.getContractId()), paymentPlanMapper.selectById(record.getPaymentPlanId()));
    }

    private PaymentRecordVO toPaymentRecordVo(PaymentRecord record, ContractMain contract, PaymentPlan plan) {
        return new PaymentRecordVO(
                record.getPaymentRecordId(),
                record.getPaymentPlanId(),
                record.getContractId(),
                contract != null ? contract.getTitle() : "",
                plan != null ? plan.getPhaseName() : "",
                money(record.getPaidAmount()),
                record.getPaidDate(),
                record.getPayer(),
                record.getReceiver(),
                record.getRemark()
        );
    }

    private List<ReminderRecordVO> toReminderVos(List<ReminderRecord> records) {
        Map<Long, FulfillmentPlan> plans = planMap(records.stream().map(ReminderRecord::getPlanId).toList());
        Map<Long, ContractMain> contracts = contractMap(records.stream().map(ReminderRecord::getContractId).toList());
        return records.stream()
                .filter(record -> contractService.canAccess(contracts.get(record.getContractId())))
                .map(record -> {
                    FulfillmentPlan plan = plans.get(record.getPlanId());
                    ContractMain contract = contracts.get(record.getContractId());
                    return new ReminderRecordVO(
                            record.getReminderId(),
                            record.getPlanId(),
                            record.getContractId(),
                            contract != null ? contract.getTitle() : "",
                            plan != null ? plan.getNodeName() : "",
                            record.getReminderLevel(),
                            record.getReminderDate(),
                            record.getChannel(),
                            record.getReceiver(),
                            record.getContent(),
                            record.getSendStatus(),
                            record.getSentAt()
                    );
                })
                .toList();
    }

    private boolean prerequisiteCompleted(PaymentPlan plan) {
        if (!StringUtils.hasText(plan.getPrerequisiteDelivery())) {
            return true;
        }
        List<DeliverableVO> deliverables = listDeliverables(plan.getContractId());
        for (String name : plan.getPrerequisiteDelivery().split("[,，、]")) {
            String normalized = name.trim();
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            boolean matched = deliverables.stream()
                    .anyMatch(item -> Boolean.TRUE.equals(item.confirmed())
                            && (normalized.equals(item.deliverableName()) || normalized.equals(item.deliverableType())));
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private BigDecimal paidAmount(Long paymentPlanId) {
        return paymentRecordMapper.selectList(new LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getPaymentPlanId, paymentPlanId))
                .stream()
                .map(PaymentRecord::getPaidAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String paymentStatus(BigDecimal planned, BigDecimal paid, long overdueDays) {
        if (planned.compareTo(BigDecimal.ZERO) > 0 && paid.compareTo(planned) >= 0) {
            return "PAID";
        }
        if (overdueDays > 0) {
            return "OVERDUE";
        }
        if (paid.compareTo(BigDecimal.ZERO) > 0) {
            return "PARTIAL";
        }
        return "UNPAID";
    }

    private String responsibilityHint(String status, long overdueDays, boolean prerequisiteCompleted) {
        String hint;
        if ("PAID".equals(status)) {
            hint = "已到账，无需责任提示";
        } else if (overdueDays <= 0) {
            hint = "未到付款期限，持续跟踪";
        } else if (prerequisiteCompleted) {
            hint = "到期未付款且前置交付已完成，提示甲方延迟支付";
        } else {
            hint = "前置交付未完成，提示待人工判断乙方履约责任";
        }
        return hint + "（" + RESPONSIBILITY_DISCLAIMER + "）";
    }

    private String warningLevel(FulfillmentPlan plan) {
        if (plan == null || CLOSED_STATUS.contains(plan.getStatus())) {
            return "NONE";
        }
        if (plan.getDueDate() == null) {
            return "NORMAL";
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(), plan.getDueDate());
        if (days < 0 || "OVERDUE".equals(plan.getStatus())) {
            return "OVERDUE";
        }
        if (days <= 3) {
            return "LEVEL3";
        }
        if (days <= 7) {
            return "LEVEL2";
        }
        if (days <= 15) {
            return "LEVEL1";
        }
        return "NORMAL";
    }

    private String reminderContent(FulfillmentPlanVO plan) {
        String daysText = plan.daysLeft() == null
                ? "待确认"
                : (plan.daysLeft() < 0 ? "已逾期" + Math.abs(plan.daysLeft()) + "天" : "剩余" + plan.daysLeft() + "天");
        return "合同【" + plan.contractTitle() + "】履约节点【" + plan.nodeName() + "】" + daysText + "，预警等级：" + plan.warningLevel();
    }

    private BigDecimal money(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private String formatPercent(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }

    private Map<Long, ContractMain> contractMap(List<Long> ids) {
        List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        return contractMapper.selectBatchIds(distinctIds).stream()
                .collect(Collectors.toMap(ContractMain::getContractId, Function.identity()));
    }

    private Map<Long, FulfillmentPlan> planMap(List<Long> ids) {
        List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        return planMapper.selectBatchIds(distinctIds).stream()
                .collect(Collectors.toMap(FulfillmentPlan::getPlanId, Function.identity()));
    }

    private Map<Long, PaymentPlan> paymentPlanMap(List<Long> ids) {
        List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        return paymentPlanMapper.selectBatchIds(distinctIds).stream()
                .collect(Collectors.toMap(PaymentPlan::getPaymentPlanId, Function.identity()));
    }
}
