package cupk.smartcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import cupk.smartcontract.entity.AiTaskRecord;
import cupk.smartcontract.entity.ArchiveRecord;
import cupk.smartcontract.entity.ContractMain;
import cupk.smartcontract.entity.ContractVersion;
import cupk.smartcontract.entity.FileInfo;
import cupk.smartcontract.entity.FulfillmentDelayApproval;
import cupk.smartcontract.entity.FulfillmentDeliverable;
import cupk.smartcontract.entity.FulfillmentPlan;
import cupk.smartcontract.entity.FulfillmentProgressLog;
import cupk.smartcontract.entity.FulfillmentVoucher;
import cupk.smartcontract.entity.OperationLog;
import cupk.smartcontract.entity.PaymentPlan;
import cupk.smartcontract.entity.PaymentRecord;
import cupk.smartcontract.entity.ReminderRecord;
import cupk.smartcontract.mapper.AiTaskRecordMapper;
import cupk.smartcontract.mapper.ContractMainMapper;
import cupk.smartcontract.mapper.FileInfoMapper;
import cupk.smartcontract.mapper.FulfillmentDelayApprovalMapper;
import cupk.smartcontract.mapper.FulfillmentDeliverableMapper;
import cupk.smartcontract.mapper.FulfillmentPlanMapper;
import cupk.smartcontract.mapper.FulfillmentProgressLogMapper;
import cupk.smartcontract.mapper.FulfillmentVoucherMapper;
import cupk.smartcontract.mapper.OperationLogMapper;
import cupk.smartcontract.mapper.PaymentPlanMapper;
import cupk.smartcontract.mapper.PaymentRecordMapper;
import cupk.smartcontract.mapper.ReminderRecordMapper;
import cupk.smartcontract.security.SecurityContext;
import jakarta.annotation.PostConstruct;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class FulfillmentService {
    private static final Logger log = LoggerFactory.getLogger(FulfillmentService.class);
    private static final List<String> CLOSED_STATUS = List.of("COMPLETED", "CLOSED", "HANDLED");
    private static final String STATUS_NOT_STARTED = "NOT_STARTED";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_OVERDUE = "OVERDUE";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final String STATUS_PENDING_CONFIRM = "PENDING_CONFIRM";
    private static final String DELIVERABLE_PENDING_SUBMIT = "PENDING_SUBMIT";
    private static final String DELIVERABLE_SUBMITTED = "SUBMITTED";
    private static final String DELIVERABLE_NEED_SUPPLEMENT = "NEED_SUPPLEMENT";
    private static final String DELIVERABLE_REJECTED = "REJECTED";
    private static final String DELIVERABLE_ACCEPTED = "ACCEPTED";
    private static final String DELIVERABLE_ACCEPTANCE_PASSED = "ACCEPTANCE_PASSED";
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal DEFAULT_PENALTY_RATE = new BigDecimal("0.05");
    private static final BigDecimal AI_CONFIRM_THRESHOLD = new BigDecimal("0.80");
    private static final long LEVEL1_WARNING_DAYS = 30L;
    private static final long LEVEL2_WARNING_DAYS = 7L;
    private static final long LEVEL3_WARNING_DAYS = 1L;
    private static final Set<String> REMINDER_WARNING_LEVELS = Set.of("LEVEL1", "LEVEL2", "LEVEL3");
    private static final Map<String, String> PROGRESS_OPERATION_TEXT = Map.ofEntries(
            Map.entry("CREATE", "创建"),
            Map.entry("UPDATE", "更新"),
            Map.entry("DELETE", "删除"),
            Map.entry("DELAY_REQUEST", "延期申请"),
            Map.entry("DELAY_CONFIRM", "延期确认"),
            Map.entry("DELAY_APPROVE", "延期审批通过"),
            Map.entry("DELAY_APPROVED", "延期审批通过"),
            Map.entry("DELAY_REJECT", "延期审批驳回"),
            Map.entry("DELAY_REJECTED", "延期审批驳回"),
            Map.entry("CLOSE_OVERDUE", "逾期关闭"),
            Map.entry("OVERDUE_COMPLETE", "逾期标记完成"),
            Map.entry("OVERDUE_DELAY_REQUEST", "逾期申请延期"),
            Map.entry("OVERDUE_HANDLE", "逾期处置"),
            Map.entry("MARK_OVERDUE", "标记逾期"),
            Map.entry("VOUCHER_UPLOAD", "凭证上传"),
            Map.entry("VOUCHER_REVIEW", "凭证审核"),
            Map.entry("FULFILLMENT_PLAN_CREATE", "创建履约节点"),
            Map.entry("FULFILLMENT_PLAN_UPDATE", "更新履约节点"),
            Map.entry("FULFILLMENT_PLAN_DELAY_REQUEST", "履约节点延期申请"),
            Map.entry("FULFILLMENT_PLAN_DELAY_CONFIRM", "履约节点延期确认"),
            Map.entry("FULFILLMENT_PLAN_OVERDUE_HANDLE", "履约节点逾期处置"),
            Map.entry("FULFILLMENT_PLAN_OVERDUE_COMPLETE", "履约节点逾期标记完成"),
            Map.entry("FULFILLMENT_PLAN_OVERDUE_DELAY_REQUEST", "履约节点逾期申请延期"),
            Map.entry("FULFILLMENT_DELAY_APPROVE", "延期审批通过"),
            Map.entry("FULFILLMENT_DELAY_REJECT", "延期审批驳回"),
            Map.entry("FULFILLMENT_VOUCHER_UPLOAD", "凭证上传"),
            Map.entry("FULFILLMENT_VOUCHER_REVIEW", "凭证审核"),
            Map.entry("FULFILLMENT_DELIVERABLE_CREATE", "新增交付物"),
            Map.entry("FULFILLMENT_DELIVERABLE_UPDATE", "编辑交付物"),
            Map.entry("FULFILLMENT_DELIVERABLE_CONFIRM", "确认交付物"),
            Map.entry("FULFILLMENT_DELIVERABLE_UNCONFIRM", "取消确认交付物"),
            Map.entry("FULFILLMENT_DELIVERABLE_DELETE", "删除交付物"),
            Map.entry("FULFILLMENT_DELIVERABLE_FILE_UPLOAD", "上传交付物文件"),
            Map.entry("FULFILLMENT_DELIVERABLE_SUBMIT", "提交交付物"),
            Map.entry("FULFILLMENT_DELIVERABLE_SUPPLEMENT", "要求补充交付物"),
            Map.entry("FULFILLMENT_DELIVERABLE_REJECT", "驳回交付物"),
            Map.entry("FULFILLMENT_DELIVERABLE_ACCEPT", "确认交付物"),
            Map.entry("FULFILLMENT_DELIVERABLE_ACCEPTANCE", "交付物验收确认")
    );
    private static final String RESPONSIBILITY_DISCLAIMER = "责任归属仅作辅助提示，最终由人工确认，不自动作出法律结论。";

    private final FulfillmentPlanMapper planMapper;
    private final ReminderRecordMapper reminderMapper;
    private final FulfillmentDeliverableMapper deliverableMapper;
    private final PaymentPlanMapper paymentPlanMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final ContractMainMapper contractMapper;
    private final FileInfoMapper fileInfoMapper;
    private final FulfillmentDelayApprovalMapper delayApprovalMapper;
    private final FulfillmentProgressLogMapper progressLogMapper;
    private final FulfillmentVoucherMapper voucherMapper;
    private final ContractManagementService contractService;
    private final ContractVersionService contractVersionService;
    private final AiDraftService aiDraftService;
    private final FileStorageService fileStorageService;
    private final AiTaskRecordMapper aiTaskRecordMapper;
    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Set<Long> extractingContractIds = ConcurrentHashMap.newKeySet();
    private volatile boolean schemaReady;

    @Autowired
    public FulfillmentService(FulfillmentPlanMapper planMapper,
                              ReminderRecordMapper reminderMapper,
                              FulfillmentDeliverableMapper deliverableMapper,
                              PaymentPlanMapper paymentPlanMapper,
                              PaymentRecordMapper paymentRecordMapper,
                              ContractMainMapper contractMapper,
                              FileInfoMapper fileInfoMapper,
                              FulfillmentDelayApprovalMapper delayApprovalMapper,
                              FulfillmentProgressLogMapper progressLogMapper,
                              FulfillmentVoucherMapper voucherMapper,
                              ContractManagementService contractService,
                              ContractVersionService contractVersionService,
                              AiDraftService aiDraftService,
                              FileStorageService fileStorageService,
                              AiTaskRecordMapper aiTaskRecordMapper,
                              OperationLogMapper operationLogMapper,
                              ObjectMapper objectMapper,
                              JdbcTemplate jdbcTemplate,
                              PlatformTransactionManager transactionManager) {
        this.planMapper = planMapper;
        this.reminderMapper = reminderMapper;
        this.deliverableMapper = deliverableMapper;
        this.paymentPlanMapper = paymentPlanMapper;
        this.paymentRecordMapper = paymentRecordMapper;
        this.contractMapper = contractMapper;
        this.fileInfoMapper = fileInfoMapper;
        this.delayApprovalMapper = delayApprovalMapper;
        this.progressLogMapper = progressLogMapper;
        this.voucherMapper = voucherMapper;
        this.contractService = contractService;
        this.contractVersionService = contractVersionService;
        this.aiDraftService = aiDraftService;
        this.fileStorageService = fileStorageService;
        this.aiTaskRecordMapper = aiTaskRecordMapper;
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
    }

    public FulfillmentService(FulfillmentPlanMapper planMapper,
                              ReminderRecordMapper reminderMapper,
                              FulfillmentDeliverableMapper deliverableMapper,
                              PaymentPlanMapper paymentPlanMapper,
                              PaymentRecordMapper paymentRecordMapper,
                              ContractMainMapper contractMapper,
                              FileInfoMapper fileInfoMapper,
                              FulfillmentProgressLogMapper progressLogMapper,
                              FulfillmentVoucherMapper voucherMapper,
                              ContractManagementService contractService,
                              ContractVersionService contractVersionService,
                              AiDraftService aiDraftService,
                              FileStorageService fileStorageService,
                              AiTaskRecordMapper aiTaskRecordMapper,
                              OperationLogMapper operationLogMapper,
                              ObjectMapper objectMapper,
                              JdbcTemplate jdbcTemplate) {
        this(planMapper, reminderMapper, deliverableMapper, paymentPlanMapper, paymentRecordMapper,
                contractMapper, fileInfoMapper, null, progressLogMapper, voucherMapper, contractService,
                contractVersionService, aiDraftService, fileStorageService, aiTaskRecordMapper,
                operationLogMapper, objectMapper, jdbcTemplate, null);
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
                CREATE TABLE IF NOT EXISTS file_info (
                  file_id bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'file id',
                  object_key varchar(500) NOT NULL COMMENT 'object key',
                  file_name varchar(255) NOT NULL COMMENT 'file name',
                  file_type varchar(40) DEFAULT NULL COMMENT 'file type',
                  size bigint unsigned DEFAULT NULL COMMENT 'file size',
                  sha256 varchar(128) DEFAULT NULL COMMENT 'sha256',
                  created_by varchar(80) DEFAULT NULL COMMENT 'created by',
                  updated_by varchar(80) DEFAULT NULL COMMENT 'updated by',
                  version int NOT NULL DEFAULT 1 COMMENT 'version',
                  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
                  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
                  is_deleted tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'delete flag',
                  PRIMARY KEY (file_id),
                  KEY idx_file_sha256 (sha256)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='file info'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS fulfillment_plan (
                  plan_id bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'plan id',
                  contract_id bigint unsigned NOT NULL COMMENT 'contract id',
                  node_name varchar(120) NOT NULL COMMENT 'node name',
                  plan_type varchar(40) NOT NULL DEFAULT 'OTHER' COMMENT 'plan type',
                  due_date date DEFAULT NULL COMMENT 'due date',
                  status varchar(40) NOT NULL DEFAULT 'NOT_STARTED' COMMENT 'status',
                  progress tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'progress',
                  owner_name varchar(80) DEFAULT NULL COMMENT 'owner name',
                  source_type varchar(40) NOT NULL DEFAULT 'MANUAL' COMMENT 'source type',
                  extracted_rule varchar(200) DEFAULT NULL COMMENT 'extract rule',
                  source_clause varchar(500) DEFAULT NULL COMMENT 'source clause',
                  ai_confidence decimal(5,4) DEFAULT NULL COMMENT 'ai confidence',
                  ai_extracted tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'ai extracted flag',
                  confirm_status varchar(40) NOT NULL DEFAULT 'CONFIRMED' COMMENT 'confirm status',
                  overdue_days int unsigned NOT NULL DEFAULT 0 COMMENT 'current overdue days',
                  last_overdue_at datetime DEFAULT NULL COMMENT 'last overdue marked time',
                  actual_completed_date date DEFAULT NULL COMMENT 'actual completed date',
                  delay_status varchar(40) NOT NULL DEFAULT 'NONE' COMMENT 'delay approval status',
                  delay_requested_due_date date DEFAULT NULL COMMENT 'requested due date',
                  delay_reason varchar(500) DEFAULT NULL COMMENT 'delay reason',
                  delay_requested_by varchar(80) DEFAULT NULL COMMENT 'delay requester',
                  delay_requested_at datetime DEFAULT NULL COMMENT 'delay requested time',
                  delay_confirmed_by varchar(80) DEFAULT NULL COMMENT 'delay confirmer',
                  delay_confirmed_at datetime DEFAULT NULL COMMENT 'delay confirmed time',
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
                CREATE TABLE IF NOT EXISTS fulfillment_progress_log (
                  log_id bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'log id',
                  plan_id bigint unsigned NOT NULL COMMENT 'plan id',
                  contract_id bigint unsigned NOT NULL COMMENT 'contract id',
                  operation varchar(80) NOT NULL COMMENT 'operation',
                  before_status varchar(40) DEFAULT NULL COMMENT 'before status',
                  after_status varchar(40) DEFAULT NULL COMMENT 'after status',
                  before_value text DEFAULT NULL COMMENT 'before value',
                  after_value text DEFAULT NULL COMMENT 'after value',
                  operator_id bigint unsigned DEFAULT NULL COMMENT 'operator id',
                  operator_name varchar(80) DEFAULT NULL COMMENT 'operator name',
                  operate_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'operate time',
                  remark varchar(500) DEFAULT NULL COMMENT 'remark',
                  client_ip varchar(80) DEFAULT NULL COMMENT 'client ip',
                  is_deleted tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'delete flag',
                  PRIMARY KEY (log_id),
                  KEY idx_progress_plan (plan_id, operate_time),
                  KEY idx_progress_contract (contract_id, operate_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='fulfillment progress log'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS fulfillment_voucher (
                  voucher_id bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'voucher id',
                  plan_id bigint unsigned NOT NULL COMMENT 'plan id',
                  contract_id bigint unsigned NOT NULL COMMENT 'contract id',
                  file_id bigint unsigned NOT NULL COMMENT 'file id',
                  voucher_type varchar(60) NOT NULL DEFAULT 'PROGRESS' COMMENT 'voucher type',
                  review_status varchar(40) NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT 'review status',
                  reviewer_id bigint unsigned DEFAULT NULL COMMENT 'reviewer id',
                  reviewer_name varchar(80) DEFAULT NULL COMMENT 'reviewer name',
                  reviewed_at datetime DEFAULT NULL COMMENT 'review time',
                  uploaded_by bigint unsigned DEFAULT NULL COMMENT 'uploader id',
                  uploaded_by_name varchar(80) DEFAULT NULL COMMENT 'uploader name',
                  uploaded_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'upload time',
                  remark varchar(500) DEFAULT NULL COMMENT 'remark',
                  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
                  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
                  is_deleted tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'delete flag',
                  PRIMARY KEY (voucher_id),
                  KEY idx_voucher_plan (plan_id, uploaded_at),
                  KEY idx_voucher_contract (contract_id, uploaded_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='fulfillment voucher'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS fulfillment_delay_approval (
                  approval_id bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'approval id',
                  plan_id bigint unsigned NOT NULL COMMENT 'plan id',
                  contract_id bigint unsigned NOT NULL COMMENT 'contract id',
                  original_due_date date DEFAULT NULL COMMENT 'original due date',
                  requested_due_date date NOT NULL COMMENT 'requested due date',
                  delay_reason varchar(500) NOT NULL COMMENT 'delay reason',
                  status varchar(40) NOT NULL DEFAULT 'PENDING' COMMENT 'approval status',
                  requester_id bigint unsigned DEFAULT NULL COMMENT 'requester id',
                  requester_name varchar(80) DEFAULT NULL COMMENT 'requester name',
                  requested_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'request time',
                  approver_id bigint unsigned DEFAULT NULL COMMENT 'approver id',
                  approver_name varchar(80) DEFAULT NULL COMMENT 'approver name',
                  approved_at datetime DEFAULT NULL COMMENT 'approval time',
                  reject_reason varchar(500) DEFAULT NULL COMMENT 'reject reason',
                  notice_status varchar(40) NOT NULL DEFAULT 'PUSHED' COMMENT 'notice status',
                  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
                  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
                  is_deleted tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'delete flag',
                  PRIMARY KEY (approval_id),
                  KEY idx_delay_plan (plan_id, status),
                  KEY idx_delay_contract (contract_id, requested_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='fulfillment delay approval'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS reminder_record (
                  reminder_id bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'reminder id',
                  plan_id bigint unsigned NOT NULL COMMENT 'plan id',
                  contract_id bigint unsigned NOT NULL COMMENT 'contract id',
                  reminder_level varchar(40) NOT NULL COMMENT 'reminder level',
                  reminder_date date NOT NULL COMMENT 'reminder date',
                  channel varchar(40) NOT NULL DEFAULT 'IN_APP' COMMENT 'channel',
                  receiver varchar(500) DEFAULT NULL COMMENT 'receiver',
                  content varchar(500) NOT NULL COMMENT 'content',
                  send_status varchar(40) NOT NULL DEFAULT 'SENT' COMMENT 'send status',
                  sent_at datetime NOT NULL COMMENT 'sent time',
                  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
                  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
                  is_deleted tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'delete flag',
                  PRIMARY KEY (reminder_id),
                  KEY idx_reminder_plan (plan_id, reminder_level, channel, reminder_date),
                  KEY idx_reminder_contract (contract_id, sent_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='reminder record'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_task_record (
                  task_id bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'task id',
                  contract_id bigint unsigned DEFAULT NULL COMMENT 'contract id',
                  task_type varchar(80) NOT NULL COMMENT 'task type',
                  model_name varchar(120) DEFAULT NULL COMMENT 'model name',
                  prompt_hash varchar(128) DEFAULT NULL COMMENT 'prompt hash',
                  token_usage int DEFAULT NULL COMMENT 'token usage',
                  status varchar(40) NOT NULL DEFAULT 'RUNNING' COMMENT 'task status',
                  input_summary varchar(500) DEFAULT NULL COMMENT 'input summary',
                  output_summary varchar(500) DEFAULT NULL COMMENT 'output summary',
                  error_reason varchar(500) DEFAULT NULL COMMENT 'error reason',
                  duration_ms bigint DEFAULT NULL COMMENT 'duration milliseconds',
                  created_by varchar(80) DEFAULT NULL COMMENT 'created by',
                  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
                  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
                  PRIMARY KEY (task_id),
                  KEY idx_ai_task_contract (contract_id, task_type, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='ai task record'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS operation_log (
                  log_id bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'log id',
                  user_id bigint unsigned DEFAULT NULL COMMENT 'user id',
                  operation varchar(80) NOT NULL COMMENT 'operation',
                  target_type varchar(80) NOT NULL COMMENT 'target type',
                  target_id bigint unsigned DEFAULT NULL COMMENT 'target id',
                  ip varchar(80) DEFAULT NULL COMMENT 'ip',
                  result varchar(80) DEFAULT NULL COMMENT 'result',
                  before_content text DEFAULT NULL COMMENT 'before content',
                  after_content text DEFAULT NULL COMMENT 'after content',
                  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
                  PRIMARY KEY (log_id),
                  KEY idx_operation_target (target_type, target_id, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='operation log'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS fulfillment_overdue_history (
                  history_id bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'history id',
                  plan_id bigint unsigned NOT NULL COMMENT 'plan id',
                  contract_id bigint unsigned NOT NULL COMMENT 'contract id',
                  node_name varchar(120) NOT NULL COMMENT 'node name snapshot',
                  due_date date DEFAULT NULL COMMENT 'overdue due date',
                  overdue_days int unsigned NOT NULL DEFAULT 0 COMMENT 'overdue days',
                  status varchar(40) NOT NULL DEFAULT 'OPEN' COMMENT 'history status',
                  started_at datetime NOT NULL COMMENT 'overdue started time',
                  resolved_at datetime DEFAULT NULL COMMENT 'resolved time',
                  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
                  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
                  is_deleted tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'delete flag',
                  PRIMARY KEY (history_id),
                  KEY idx_overdue_history_plan (plan_id, status),
                  KEY idx_overdue_history_contract (contract_id, started_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='fulfillment overdue history'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS fulfillment_deliverable (
                  deliverable_id bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'deliverable id',
                  plan_id bigint unsigned DEFAULT NULL COMMENT 'fulfillment plan id',
                  contract_id bigint unsigned NOT NULL COMMENT 'contract id',
                  deliverable_type varchar(60) NOT NULL COMMENT 'deliverable type',
                  deliverable_name varchar(120) NOT NULL COMMENT 'deliverable name',
                  stage_name varchar(80) NOT NULL COMMENT 'stage name',
                  confirm_method varchar(80) NOT NULL DEFAULT 'CHECKLIST' COMMENT 'confirm method',
                  status varchar(30) NOT NULL DEFAULT 'PENDING_SUBMIT' COMMENT 'deliverable workflow status',
                  confirmed tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'confirmed flag',
                  confirm_status varchar(40) NOT NULL DEFAULT 'CONFIRMED' COMMENT 'ai/manual confirm status',
                  source_clause varchar(500) DEFAULT NULL COMMENT 'source clause',
                  ai_confidence decimal(5,4) DEFAULT NULL COMMENT 'ai confidence',
                  ai_extracted tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'ai extracted flag',
                  file_id bigint unsigned DEFAULT NULL COMMENT 'uploaded file id',
                  confirmer varchar(80) DEFAULT NULL COMMENT 'confirmer',
                  confirmed_at datetime DEFAULT NULL COMMENT 'confirmed time',
                  acceptance_passed tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'acceptance passed flag',
                  accepted_by varchar(80) DEFAULT NULL COMMENT 'acceptance operator',
                  accepted_at datetime DEFAULT NULL COMMENT 'acceptance time',
                  submitted_by varchar(80) DEFAULT NULL COMMENT 'submitter',
                  submitted_at datetime DEFAULT NULL COMMENT 'submitted time',
                  reviewer_name varchar(80) DEFAULT NULL COMMENT 'reviewer',
                  reviewed_at datetime DEFAULT NULL COMMENT 'reviewed time',
                  review_comment varchar(500) DEFAULT NULL COMMENT 'review comment',
                  submission_version int unsigned NOT NULL DEFAULT 0 COMMENT 'submission version',
                  remark varchar(500) DEFAULT NULL COMMENT 'remark',
                  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
                  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
                  is_deleted tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'delete flag',
                  PRIMARY KEY (deliverable_id),
                  KEY idx_deliverable_plan (plan_id, confirmed),
                  KEY idx_deliverable_contract (contract_id, deliverable_type, confirmed)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='fulfillment deliverable'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS payment_plan (
                  payment_plan_id bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'payment plan id',
                  contract_id bigint unsigned NOT NULL COMMENT 'contract id',
                  fulfillment_plan_id bigint unsigned DEFAULT NULL COMMENT 'fulfillment plan id',
                  phase_name varchar(120) NOT NULL COMMENT 'phase name',
                  percentage decimal(8,2) NOT NULL DEFAULT 0.00 COMMENT 'payment percentage',
                  planned_amount decimal(15,2) NOT NULL DEFAULT 0.00 COMMENT 'planned amount',
                  due_date date NOT NULL COMMENT 'due date',
                  payee varchar(120) DEFAULT NULL COMMENT 'payee',
                  payment_condition varchar(300) DEFAULT NULL COMMENT 'payment condition',
                  condition_type varchar(40) NOT NULL DEFAULT 'NONE' COMMENT 'condition type',
                  condition_status varchar(40) NOT NULL DEFAULT 'SATISFIED' COMMENT 'condition status',
                  prerequisite_delivery varchar(300) DEFAULT NULL COMMENT 'prerequisite delivery',
                  penalty_rate decimal(8,4) NOT NULL DEFAULT 0.0500 COMMENT 'daily penalty rate percent',
                  status varchar(40) NOT NULL DEFAULT 'READY_TO_PAY' COMMENT 'status',
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
                  bank_serial_no varchar(120) DEFAULT NULL COMMENT 'bank serial no',
                  handler_name varchar(80) DEFAULT NULL COMMENT 'handler name',
                  voucher_file_id bigint unsigned DEFAULT NULL COMMENT 'voucher file id',
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
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS invoice_record (
                  invoice_id bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'invoice id',
                  payment_plan_id bigint unsigned NOT NULL COMMENT 'payment plan id',
                  contract_id bigint unsigned NOT NULL COMMENT 'contract id',
                  invoice_no varchar(120) NOT NULL COMMENT 'invoice no',
                  invoice_amount decimal(15,2) NOT NULL DEFAULT 0.00 COMMENT 'invoice amount',
                  invoice_date date DEFAULT NULL COMMENT 'invoice date',
                  invoice_status varchar(40) NOT NULL DEFAULT 'RECEIVED' COMMENT 'invoice status',
                  file_id bigint unsigned DEFAULT NULL COMMENT 'file id',
                  remark varchar(500) DEFAULT NULL COMMENT 'remark',
                  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
                  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
                  is_deleted tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'delete flag',
                  PRIMARY KEY (invoice_id),
                  KEY idx_invoice_plan (payment_plan_id, invoice_status),
                  KEY idx_invoice_contract (contract_id, invoice_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='invoice record'
                """);
        ensureLegacyColumns();
        ensureMemberEColumns();
    }

    private void ensureLegacyColumns() {
        ensureColumn("file_info", "object_key", "`object_key` varchar(500) NOT NULL DEFAULT '' COMMENT 'object key'");
        ensureColumn("file_info", "file_name", "`file_name` varchar(255) NOT NULL DEFAULT '' COMMENT 'file name'");
        ensureColumn("file_info", "file_type", "`file_type` varchar(40) DEFAULT NULL COMMENT 'file type'");
        ensureColumn("file_info", "size", "`size` bigint unsigned DEFAULT NULL COMMENT 'file size'");
        ensureColumn("file_info", "sha256", "`sha256` varchar(128) DEFAULT NULL COMMENT 'sha256'");
        ensureColumn("file_info", "created_by", "`created_by` varchar(80) DEFAULT NULL COMMENT 'created by'");
        ensureColumn("file_info", "updated_by", "`updated_by` varchar(80) DEFAULT NULL COMMENT 'updated by'");
        ensureColumn("file_info", "version", "`version` int NOT NULL DEFAULT 1 COMMENT 'version'");
        ensureColumn("file_info", "created_at", "`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time'");
        ensureColumn("file_info", "updated_at", "`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time'");
        ensureColumn("file_info", "is_deleted", "`is_deleted` tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'delete flag'");

        ensureColumn("fulfillment_plan", "node_name", "`node_name` varchar(120) NOT NULL DEFAULT '' COMMENT 'node name'");
        ensureColumn("fulfillment_plan", "plan_type", "`plan_type` varchar(40) NOT NULL DEFAULT 'OTHER' COMMENT 'plan type'");
        ensureColumn("fulfillment_plan", "due_date", "`due_date` date DEFAULT NULL COMMENT 'due date'");
        modifyColumnIfExists("fulfillment_plan", "due_date", "`due_date` date DEFAULT NULL COMMENT 'due date'");
        ensureColumn("fulfillment_plan", "progress", "`progress` tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'progress'");
        ensureColumn("fulfillment_plan", "owner_name", "`owner_name` varchar(80) DEFAULT NULL COMMENT 'owner name'");
        ensureColumn("fulfillment_plan", "source_type", "`source_type` varchar(40) NOT NULL DEFAULT 'MANUAL' COMMENT 'source type'");
        ensureColumn("fulfillment_plan", "extracted_rule", "`extracted_rule` varchar(200) DEFAULT NULL COMMENT 'extract rule'");
        ensureColumn("fulfillment_plan", "source_clause", "`source_clause` varchar(500) DEFAULT NULL COMMENT 'source clause'");
        ensureColumn("fulfillment_plan", "ai_confidence", "`ai_confidence` decimal(5,4) DEFAULT NULL COMMENT 'ai confidence'");
        ensureColumn("fulfillment_plan", "ai_extracted", "`ai_extracted` tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'ai extracted flag'");
        ensureColumn("fulfillment_plan", "confirm_status", "`confirm_status` varchar(40) NOT NULL DEFAULT 'CONFIRMED' COMMENT 'confirm status'");
        ensureColumn("fulfillment_plan", "overdue_days", "`overdue_days` int unsigned NOT NULL DEFAULT 0 COMMENT 'current overdue days'");
        ensureColumn("fulfillment_plan", "last_overdue_at", "`last_overdue_at` datetime DEFAULT NULL COMMENT 'last overdue marked time'");
        ensureColumn("fulfillment_plan", "actual_completed_date", "`actual_completed_date` date DEFAULT NULL COMMENT 'actual completed date'");
        ensureColumn("fulfillment_plan", "delay_status", "`delay_status` varchar(40) NOT NULL DEFAULT 'NONE' COMMENT 'delay approval status'");
        ensureColumn("fulfillment_plan", "delay_requested_due_date", "`delay_requested_due_date` date DEFAULT NULL COMMENT 'requested due date'");
        ensureColumn("fulfillment_plan", "delay_reason", "`delay_reason` varchar(500) DEFAULT NULL COMMENT 'delay reason'");
        ensureColumn("fulfillment_plan", "delay_requested_by", "`delay_requested_by` varchar(80) DEFAULT NULL COMMENT 'delay requester'");
        ensureColumn("fulfillment_plan", "delay_requested_at", "`delay_requested_at` datetime DEFAULT NULL COMMENT 'delay requested time'");
        ensureColumn("fulfillment_plan", "delay_confirmed_by", "`delay_confirmed_by` varchar(80) DEFAULT NULL COMMENT 'delay confirmer'");
        ensureColumn("fulfillment_plan", "delay_confirmed_at", "`delay_confirmed_at` datetime DEFAULT NULL COMMENT 'delay confirmed time'");
        ensureColumn("fulfillment_plan", "delay_rejected_by", "`delay_rejected_by` varchar(80) DEFAULT NULL COMMENT 'delay rejecter'");
        ensureColumn("fulfillment_plan", "delay_rejected_at", "`delay_rejected_at` datetime DEFAULT NULL COMMENT 'delay rejected time'");
        ensureColumn("fulfillment_plan", "delay_reject_reason", "`delay_reject_reason` varchar(500) DEFAULT NULL COMMENT 'delay reject reason'");
        ensureColumn("fulfillment_plan", "remark", "`remark` varchar(500) DEFAULT NULL COMMENT 'remark'");
        ensureColumn("fulfillment_plan", "handled_at", "`handled_at` datetime DEFAULT NULL COMMENT 'handled time'");
        modifyColumnIfExists("fulfillment_plan", "milestone_name", "`milestone_name` varchar(150) NOT NULL DEFAULT '' COMMENT 'legacy milestone name'");
        modifyColumnIfExists("fulfillment_plan", "owner_id", "`owner_id` bigint unsigned NOT NULL DEFAULT 1 COMMENT 'legacy owner id'");

        ensureColumn("reminder_record", "plan_id", "`plan_id` bigint unsigned NOT NULL DEFAULT 0 COMMENT 'plan id'");
        ensureColumn("reminder_record", "reminder_level", "`reminder_level` varchar(40) NOT NULL DEFAULT 'LEVEL1' COMMENT 'reminder level'");
        ensureColumn("reminder_record", "reminder_date", "`reminder_date` date DEFAULT NULL COMMENT 'reminder date'");
        ensureColumn("reminder_record", "channel", "`channel` varchar(40) NOT NULL DEFAULT 'IN_APP' COMMENT 'channel'");
        ensureColumn("reminder_record", "receiver", "`receiver` varchar(500) DEFAULT NULL COMMENT 'receiver'");
        modifyColumnIfExists("reminder_record", "receiver", "`receiver` varchar(500) DEFAULT NULL COMMENT 'receiver'");
        ensureColumn("reminder_record", "content", "`content` varchar(500) NOT NULL DEFAULT '' COMMENT 'content'");
        ensureColumn("reminder_record", "send_status", "`send_status` varchar(40) NOT NULL DEFAULT 'SENT' COMMENT 'send status'");
        ensureColumn("reminder_record", "sent_at", "`sent_at` datetime DEFAULT NULL COMMENT 'sent time'");
        ensureColumn("reminder_record", "created_at", "`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time'");
        ensureColumn("reminder_record", "updated_at", "`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time'");
        ensureColumn("reminder_record", "is_deleted", "`is_deleted` tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'delete flag'");
        modifyColumnIfExists("reminder_record", "reminder_type", "`reminder_type` varchar(50) NOT NULL DEFAULT '' COMMENT 'legacy reminder type'");
        modifyColumnIfExists("reminder_record", "send_time", "`send_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'legacy send time'");
        modifyColumnIfExists("reminder_record", "receiver_id", "`receiver_id` bigint unsigned NOT NULL DEFAULT 1 COMMENT 'legacy receiver id'");

        ensureColumn("ai_task_record", "contract_id", "`contract_id` bigint unsigned DEFAULT NULL COMMENT 'contract id'");
        ensureColumn("ai_task_record", "task_type", "`task_type` varchar(80) NOT NULL DEFAULT '' COMMENT 'task type'");
        ensureColumn("ai_task_record", "model_name", "`model_name` varchar(120) DEFAULT NULL COMMENT 'model name'");
        ensureColumn("ai_task_record", "prompt_hash", "`prompt_hash` varchar(128) DEFAULT NULL COMMENT 'prompt hash'");
        ensureColumn("ai_task_record", "token_usage", "`token_usage` int DEFAULT NULL COMMENT 'token usage'");
        ensureColumn("ai_task_record", "status", "`status` varchar(40) NOT NULL DEFAULT 'RUNNING' COMMENT 'task status'");
        ensureColumn("ai_task_record", "input_summary", "`input_summary` varchar(500) DEFAULT NULL COMMENT 'input summary'");
        ensureColumn("ai_task_record", "output_summary", "`output_summary` varchar(500) DEFAULT NULL COMMENT 'output summary'");
        ensureColumn("ai_task_record", "error_reason", "`error_reason` varchar(500) DEFAULT NULL COMMENT 'error reason'");
        ensureColumn("ai_task_record", "duration_ms", "`duration_ms` bigint DEFAULT NULL COMMENT 'duration milliseconds'");
        ensureColumn("ai_task_record", "created_by", "`created_by` varchar(80) DEFAULT NULL COMMENT 'created by'");
        ensureColumn("ai_task_record", "created_at", "`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time'");
        ensureColumn("ai_task_record", "updated_at", "`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time'");

        ensureColumn("operation_log", "before_content", "`before_content` text DEFAULT NULL COMMENT 'before content'");
        ensureColumn("operation_log", "after_content", "`after_content` text DEFAULT NULL COMMENT 'after content'");

        ensureColumn("fulfillment_progress_log", "operation", "`operation` varchar(80) NOT NULL DEFAULT 'UPDATE' COMMENT 'operation'");
        ensureColumn("fulfillment_progress_log", "before_status", "`before_status` varchar(40) DEFAULT NULL COMMENT 'before status'");
        ensureColumn("fulfillment_progress_log", "after_status", "`after_status` varchar(40) DEFAULT NULL COMMENT 'after status'");
        ensureColumn("fulfillment_progress_log", "before_value", "`before_value` text DEFAULT NULL COMMENT 'before value'");
        ensureColumn("fulfillment_progress_log", "after_value", "`after_value` text DEFAULT NULL COMMENT 'after value'");
        ensureColumn("fulfillment_progress_log", "operator_id", "`operator_id` bigint unsigned DEFAULT NULL COMMENT 'operator id'");
        ensureColumn("fulfillment_progress_log", "operator_name", "`operator_name` varchar(80) DEFAULT NULL COMMENT 'operator name'");
        ensureColumn("fulfillment_progress_log", "operate_time", "`operate_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'operate time'");
        ensureColumn("fulfillment_progress_log", "remark", "`remark` varchar(500) DEFAULT NULL COMMENT 'remark'");
        ensureColumn("fulfillment_progress_log", "client_ip", "`client_ip` varchar(80) DEFAULT NULL COMMENT 'client ip'");
        ensureColumn("fulfillment_progress_log", "is_deleted", "`is_deleted` tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'delete flag'");

        ensureColumn("fulfillment_voucher", "voucher_type", "`voucher_type` varchar(60) NOT NULL DEFAULT 'PROGRESS' COMMENT 'voucher type'");
        ensureColumn("fulfillment_voucher", "review_status", "`review_status` varchar(40) NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT 'review status'");
        ensureColumn("fulfillment_voucher", "reviewer_id", "`reviewer_id` bigint unsigned DEFAULT NULL COMMENT 'reviewer id'");
        ensureColumn("fulfillment_voucher", "reviewer_name", "`reviewer_name` varchar(80) DEFAULT NULL COMMENT 'reviewer name'");
        ensureColumn("fulfillment_voucher", "reviewed_at", "`reviewed_at` datetime DEFAULT NULL COMMENT 'review time'");
        ensureColumn("fulfillment_voucher", "uploaded_by", "`uploaded_by` bigint unsigned DEFAULT NULL COMMENT 'uploader id'");
        ensureColumn("fulfillment_voucher", "uploaded_by_name", "`uploaded_by_name` varchar(80) DEFAULT NULL COMMENT 'uploader name'");
        ensureColumn("fulfillment_voucher", "uploaded_at", "`uploaded_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'upload time'");
        ensureColumn("fulfillment_voucher", "remark", "`remark` varchar(500) DEFAULT NULL COMMENT 'remark'");

        ensureColumn("fulfillment_delay_approval", "original_due_date", "`original_due_date` date DEFAULT NULL COMMENT 'original due date'");
        ensureColumn("fulfillment_delay_approval", "requested_due_date", "`requested_due_date` date DEFAULT NULL COMMENT 'requested due date'");
        ensureColumn("fulfillment_delay_approval", "delay_reason", "`delay_reason` varchar(500) NOT NULL DEFAULT '' COMMENT 'delay reason'");
        ensureColumn("fulfillment_delay_approval", "status", "`status` varchar(40) NOT NULL DEFAULT 'PENDING' COMMENT 'approval status'");
        ensureColumn("fulfillment_delay_approval", "requester_id", "`requester_id` bigint unsigned DEFAULT NULL COMMENT 'requester id'");
        ensureColumn("fulfillment_delay_approval", "requester_name", "`requester_name` varchar(80) DEFAULT NULL COMMENT 'requester name'");
        ensureColumn("fulfillment_delay_approval", "requested_at", "`requested_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'request time'");
        ensureColumn("fulfillment_delay_approval", "approver_id", "`approver_id` bigint unsigned DEFAULT NULL COMMENT 'approver id'");
        ensureColumn("fulfillment_delay_approval", "approver_name", "`approver_name` varchar(80) DEFAULT NULL COMMENT 'approver name'");
        ensureColumn("fulfillment_delay_approval", "approved_at", "`approved_at` datetime DEFAULT NULL COMMENT 'approval time'");
        ensureColumn("fulfillment_delay_approval", "reject_reason", "`reject_reason` varchar(500) DEFAULT NULL COMMENT 'reject reason'");
        ensureColumn("fulfillment_delay_approval", "notice_status", "`notice_status` varchar(40) NOT NULL DEFAULT 'PUSHED' COMMENT 'notice status'");
        ensureColumn("fulfillment_delay_approval", "created_at", "`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time'");
        ensureColumn("fulfillment_delay_approval", "updated_at", "`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time'");
        ensureColumn("fulfillment_delay_approval", "is_deleted", "`is_deleted` tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'delete flag'");

        ensureColumn("fulfillment_overdue_history", "resolution_type", "`resolution_type` varchar(40) DEFAULT NULL COMMENT 'resolution type'");
        ensureColumn("fulfillment_overdue_history", "disposal_remark", "`disposal_remark` varchar(500) DEFAULT NULL COMMENT 'disposal remark'");
        ensureColumn("fulfillment_overdue_history", "actual_completed_date", "`actual_completed_date` date DEFAULT NULL COMMENT 'actual completed date'");
        ensureColumn("fulfillment_overdue_history", "delay_approval_id", "`delay_approval_id` bigint unsigned DEFAULT NULL COMMENT 'delay approval id'");
        ensureColumn("fulfillment_overdue_history", "resolved_by", "`resolved_by` varchar(80) DEFAULT NULL COMMENT 'resolved by'");
    }

    private void ensureMemberEColumns() {
        ensureColumn("fulfillment_deliverable", "plan_id", "`plan_id` bigint unsigned DEFAULT NULL COMMENT 'fulfillment plan id'");
        ensureColumn("fulfillment_deliverable", "confirm_status", "`confirm_status` varchar(40) NOT NULL DEFAULT 'CONFIRMED' COMMENT 'ai/manual confirm status'");
        ensureColumn("fulfillment_deliverable", "source_clause", "`source_clause` varchar(500) DEFAULT NULL COMMENT 'source clause'");
        ensureColumn("fulfillment_deliverable", "ai_confidence", "`ai_confidence` decimal(5,4) DEFAULT NULL COMMENT 'ai confidence'");
        ensureColumn("fulfillment_deliverable", "ai_extracted", "`ai_extracted` tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'ai extracted flag'");
        ensureColumn("fulfillment_deliverable", "file_id", "`file_id` bigint unsigned DEFAULT NULL COMMENT 'uploaded file id'");
        ensureColumn("fulfillment_deliverable", "stage_name", "`stage_name` varchar(80) NOT NULL DEFAULT '' COMMENT 'stage name'");
        ensureColumn("fulfillment_deliverable", "confirm_method", "`confirm_method` varchar(80) NOT NULL DEFAULT '逐项勾选确认' COMMENT 'confirm method'");
        ensureColumn("fulfillment_deliverable", "status", "`status` varchar(30) NOT NULL DEFAULT 'PENDING_SUBMIT' COMMENT 'deliverable workflow status'");
        ensureColumn("fulfillment_deliverable", "confirmed", "`confirmed` tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'confirmed flag'");
        ensureColumn("fulfillment_deliverable", "confirmer", "`confirmer` varchar(80) DEFAULT NULL COMMENT 'confirmer'");
        ensureColumn("fulfillment_deliverable", "confirmed_at", "`confirmed_at` datetime DEFAULT NULL COMMENT 'confirmed time'");
        ensureColumn("fulfillment_deliverable", "acceptance_passed", "`acceptance_passed` tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'acceptance passed flag'");
        ensureColumn("fulfillment_deliverable", "accepted_by", "`accepted_by` varchar(80) DEFAULT NULL COMMENT 'acceptance operator'");
        ensureColumn("fulfillment_deliverable", "accepted_at", "`accepted_at` datetime DEFAULT NULL COMMENT 'acceptance time'");
        ensureColumn("fulfillment_deliverable", "submitted_by", "`submitted_by` varchar(80) DEFAULT NULL COMMENT 'submitter'");
        ensureColumn("fulfillment_deliverable", "submitted_at", "`submitted_at` datetime DEFAULT NULL COMMENT 'submitted time'");
        ensureColumn("fulfillment_deliverable", "reviewer_name", "`reviewer_name` varchar(80) DEFAULT NULL COMMENT 'reviewer'");
        ensureColumn("fulfillment_deliverable", "reviewed_at", "`reviewed_at` datetime DEFAULT NULL COMMENT 'reviewed time'");
        ensureColumn("fulfillment_deliverable", "review_comment", "`review_comment` varchar(500) DEFAULT NULL COMMENT 'review comment'");
        ensureColumn("fulfillment_deliverable", "submission_version", "`submission_version` int unsigned NOT NULL DEFAULT 0 COMMENT 'submission version'");
        modifyColumnIfExists("fulfillment_deliverable", "deliverable_name", "`deliverable_name` varchar(200) NOT NULL DEFAULT '' COMMENT 'deliverable name'");
        modifyColumnIfExists("fulfillment_deliverable", "deliverable_type", "`deliverable_type` varchar(60) NOT NULL DEFAULT '' COMMENT 'deliverable type'");
        modifyColumnIfExists("fulfillment_deliverable", "contract_stage", "`contract_stage` varchar(50) NOT NULL DEFAULT '' COMMENT 'legacy contract stage'");
        jdbcTemplate.update("""
                UPDATE fulfillment_deliverable
                SET status = CASE
                    WHEN acceptance_passed = 1 THEN 'ACCEPTANCE_PASSED'
                    WHEN confirmed = 1 THEN 'ACCEPTED'
                    WHEN file_id IS NOT NULL THEN 'SUBMITTED'
                    ELSE 'PENDING_SUBMIT'
                END
                WHERE status IS NULL
                   OR status = ''
                   OR status IN ('PENDING', 'CONFIRMED')
                """);

        ensureColumn("payment_plan", "phase_name", "`phase_name` varchar(120) NOT NULL DEFAULT '' COMMENT 'phase name'");
        ensureColumn("payment_plan", "fulfillment_plan_id", "`fulfillment_plan_id` bigint unsigned DEFAULT NULL COMMENT 'fulfillment plan id'");
        ensureColumn("payment_plan", "percentage", "`percentage` decimal(8,2) NOT NULL DEFAULT 0.00 COMMENT 'payment percentage'");
        ensureColumn("payment_plan", "planned_amount", "`planned_amount` decimal(15,2) NOT NULL DEFAULT 0.00 COMMENT 'planned amount'");
        ensureColumn("payment_plan", "payee", "`payee` varchar(120) DEFAULT NULL COMMENT 'payee'");
        ensureColumn("payment_plan", "payment_condition", "`payment_condition` varchar(300) DEFAULT NULL COMMENT 'payment condition'");
        ensureColumn("payment_plan", "condition_type", "`condition_type` varchar(40) NOT NULL DEFAULT 'NONE' COMMENT 'condition type'");
        ensureColumn("payment_plan", "condition_status", "`condition_status` varchar(40) NOT NULL DEFAULT 'SATISFIED' COMMENT 'condition status'");
        ensureColumn("payment_plan", "prerequisite_delivery", "`prerequisite_delivery` varchar(300) DEFAULT NULL COMMENT 'prerequisite delivery'");
        ensureColumn("payment_plan", "penalty_rate", "`penalty_rate` decimal(8,4) NOT NULL DEFAULT 0.0500 COMMENT 'daily penalty rate percent'");
        ensureColumn("payment_plan", "remark", "`remark` varchar(500) DEFAULT NULL COMMENT 'remark'");
        modifyColumnIfExists("payment_plan", "plan_id", "`plan_id` bigint unsigned NOT NULL DEFAULT 1 COMMENT 'legacy plan id'");
        modifyColumnIfExists("payment_plan", "installment_no", "`installment_no` int NOT NULL DEFAULT 1 COMMENT 'legacy installment no'");
        modifyColumnIfExists("payment_plan", "ratio", "`ratio` decimal(5,4) NOT NULL DEFAULT 0.0000 COMMENT 'legacy ratio'");
        modifyColumnIfExists("payment_plan", "amount", "`amount` decimal(15,2) NOT NULL DEFAULT 0.00 COMMENT 'legacy amount'");

        ensureColumn("payment_record", "paid_date", "`paid_date` date DEFAULT NULL COMMENT 'paid date'");
        ensureColumn("payment_record", "bank_serial_no", "`bank_serial_no` varchar(120) DEFAULT NULL COMMENT 'bank serial no'");
        ensureColumn("payment_record", "handler_name", "`handler_name` varchar(80) DEFAULT NULL COMMENT 'handler name'");
        ensureColumn("payment_record", "voucher_file_id", "`voucher_file_id` bigint unsigned DEFAULT NULL COMMENT 'voucher file id'");
        ensureColumn("payment_record", "payer", "`payer` varchar(120) DEFAULT NULL COMMENT 'payer'");
        ensureColumn("payment_record", "receiver", "`receiver` varchar(120) DEFAULT NULL COMMENT 'receiver'");
        ensureColumn("payment_record", "remark", "`remark` varchar(500) DEFAULT NULL COMMENT 'remark'");
        modifyColumnIfExists("payment_record", "paid_amount", "`paid_amount` decimal(15,2) NOT NULL DEFAULT 0.00 COMMENT 'paid amount'");
        modifyColumnIfExists("payment_record", "paid_at", "`paid_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'legacy paid time'");

        ensureColumn("invoice_record", "payment_plan_id", "`payment_plan_id` bigint unsigned NOT NULL DEFAULT 0 COMMENT 'payment plan id'");
        ensureColumn("invoice_record", "contract_id", "`contract_id` bigint unsigned NOT NULL DEFAULT 0 COMMENT 'contract id'");
        ensureColumn("invoice_record", "invoice_no", "`invoice_no` varchar(120) NOT NULL DEFAULT '' COMMENT 'invoice no'");
        ensureColumn("invoice_record", "invoice_amount", "`invoice_amount` decimal(15,2) NOT NULL DEFAULT 0.00 COMMENT 'invoice amount'");
        ensureColumn("invoice_record", "invoice_date", "`invoice_date` date DEFAULT NULL COMMENT 'invoice date'");
        ensureColumn("invoice_record", "invoice_status", "`invoice_status` varchar(40) NOT NULL DEFAULT 'RECEIVED' COMMENT 'invoice status'");
        ensureColumn("invoice_record", "file_id", "`file_id` bigint unsigned DEFAULT NULL COMMENT 'file id'");
        ensureColumn("invoice_record", "remark", "`remark` varchar(500) DEFAULT NULL COMMENT 'remark'");
        ensureColumn("invoice_record", "created_at", "`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time'");
        ensureColumn("invoice_record", "updated_at", "`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time'");
        ensureColumn("invoice_record", "is_deleted", "`is_deleted` tinyint unsigned NOT NULL DEFAULT 0 COMMENT 'delete flag'");

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
            List<String> statuses = statusFilterValues(status);
            wrapper.in(FulfillmentPlan::getStatus, statuses);
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

    public List<FulfillmentPlanVO> extractPlans(Long contractId) {
        ensureSchemaReady();
        if (!beginExtraction(contractId)) {
            throw new IllegalStateException("该合同正在抽取履约节点，请勿重复提交，稍后刷新查看结果");
        }
        try {
            ContractMain contract = contractService.findContract(contractId);
            contractService.assertCanAccess(contractId);
            List<FulfillmentPlan> existingAiPlans = planMapper.selectList(new LambdaQueryWrapper<FulfillmentPlan>()
                    .eq(FulfillmentPlan::getContractId, contractId)
                    .eq(FulfillmentPlan::getSourceType, "AI"));
            if (!existingAiPlans.isEmpty() && existingAiPlans.stream().allMatch(this::hasAiMetadata)) {
                return listPlans(contractId, null, null);
            }

            String contractText = resolveArchivedContractText(contractId);
            LocalDate archiveDate = resolveArchiveDate(contractId);
            String sanitizedText = sanitizeContractText(contractText);
            long startedAt = System.currentTimeMillis();
            AiTaskRecord taskRecord = startAiTask(contractId, sanitizedText);
            List<FulfillmentPlan> plans;
            String taskStatus = "SUCCESS";
            String taskError = null;
            try {
                List<AiDraftService.FulfillmentNode> nodes =
                        aiDraftService.extractFulfillmentNodes(
                                sanitizedText,
                                contract.getDueDate(),
                                contract.getSignDate(),
                                archiveDate
                        );
                plans = nodes.stream()
                        .map(node -> aiPlan(contractId, contract, node, nodes, archiveDate))
                        .toList();
                if (plans.isEmpty()) {
                    plans = fallbackPlans(contractId, contract, "AI未识别到明确履约节点，按合同到期日兜底生成");
                }
            } catch (Exception ex) {
                log.warn("AI fulfillment extraction failed, fallback to due-date rules. contractId={}, reason={}",
                        contractId, ex.getMessage());
                taskStatus = "FAILED";
                taskError = rootMessage(ex);
                plans = fallbackPlans(contractId, contract, "AI抽取失败，按合同到期日兜底生成");
            }

            List<FulfillmentPlan> extractedPlans = plans;
            try {
                runInTransaction(() -> replaceExtractedPlans(contractId, extractedPlans));
                finishAiTask(taskRecord, taskStatus, aiOutputSummary(extractedPlans), taskError, startedAt);
            } catch (Exception ex) {
                finishAiTask(taskRecord, "FAILED", null, rootMessage(ex), startedAt);
                throw ex;
            }
            return listPlans(contractId, null, null);
        } finally {
            endExtraction(contractId);
        }
    }

    public void initializeMemberE(Long contractId) {
        ensureSchemaReady();
        ContractMain contract = contractService.findContract(contractId);
        contractService.assertCanAccess(contractId);
        extractPlans(contractId);
        runInTransaction(() -> {
            createAiLinkedStandardDeliverables(contractId);
            createPaymentPlansFromFulfillmentNodes(contractId);
            createStandardPaymentPlans(contract);
        });
    }

    private boolean beginExtraction(Long contractId) {
        return contractId != null && extractingContractIds.add(contractId);
    }

    private void endExtraction(Long contractId) {
        if (contractId != null) {
            extractingContractIds.remove(contractId);
        }
    }

    private void replaceExtractedPlans(Long contractId, List<FulfillmentPlan> plans) {
        planMapper.delete(new LambdaQueryWrapper<FulfillmentPlan>()
                .eq(FulfillmentPlan::getContractId, contractId)
                .in(FulfillmentPlan::getSourceType, List.of("AI", "AUTO")));
        plans.forEach(planMapper::insert);
        createPaymentPlansFromFulfillmentNodes(contractId);
        writeOperationLog("FULFILLMENT_AI_EXTRACT", "CONTRACT", contractId,
                null, aiOutputSummary(plans), "SUCCESS");
        syncOverdueStatus();
    }

    private void runInTransaction(Runnable action) {
        if (transactionTemplate == null) {
            action.run();
            return;
        }
        transactionTemplate.executeWithoutResult(status -> action.run());
    }

    @Transactional
    public FulfillmentPlanVO createPlan(FulfillmentPlanRequest request) {
        return createPlan(request, null);
    }

    @Transactional
    public FulfillmentPlanVO createPlan(FulfillmentPlanRequest request, String clientIp) {
        ensureSchemaReady();
        if (request == null || request.contractId() == null) {
            throw new IllegalArgumentException("contractId is required");
        }
        ContractMain contract = contractService.findContract(request.contractId());
        assertContractMaintainAccess(contract);
        FulfillmentPlan plan = new FulfillmentPlan();
        applyPlanRequest(plan, request);
        plan.setSourceType("MANUAL");
        plan.setExtractedRule("手动维护");
        plan.setSourceClause(null);
        plan.setAiConfidence(null);
        plan.setAiExtracted(0);
        plan.setConfirmStatus("CONFIRMED");
        plan.setOverdueDays(0);
        plan.setDelayStatus("NONE");
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        plan.setDeleted(0);
        planMapper.insert(plan);
        FulfillmentPlan saved = planMapper.selectById(plan.getPlanId());
        writeProgressLog("创建", saved.getPlanId(), saved.getContractId(), null, saved.getStatus(),
                null, planSnapshot(saved), saved.getRemark(), clientIp);
        writePlanLog("FULFILLMENT_PLAN_CREATE", saved.getPlanId(), null, planSnapshot(saved), "SUCCESS");
        return toPlanVo(saved);
    }

    @Transactional
    public FulfillmentPlanVO updatePlan(Long planId, FulfillmentPlanRequest request) {
        return updatePlan(planId, request, null);
    }

    @Transactional
    public FulfillmentPlanVO updatePlan(Long planId, FulfillmentPlanRequest request, String clientIp) {
        ensureSchemaReady();
        FulfillmentPlan plan = assertPlanMaintainAccess(planId);
        String before = planSnapshot(plan);
        String originalStatus = plan.getStatus();
        boolean originallyOverdue = STATUS_OVERDUE.equals(normalizeStatus(originalStatus));
        if (originallyOverdue && request != null) {
            boolean dueDateChanged = request.dueDate() != null && !Objects.equals(request.dueDate(), plan.getDueDate());
            boolean completing = (request.progress() != null && request.progress() >= 100)
                    || STATUS_COMPLETED.equals(normalizeStatus(request.status()))
                    || STATUS_CLOSED.equals(normalizeStatus(request.status()))
                    || request.actualCompletedDate() != null;
            if (dueDateChanged || completing) {
                throw new IllegalStateException("逾期节点完成或延期请使用“处置”功能");
            }
        }
        applyPlanRequest(plan, request);
        if (originallyOverdue) {
            plan.setStatus(STATUS_OVERDUE);
        }
        if (CLOSED_STATUS.contains(plan.getStatus())) {
            closeOpenOverdueHistory(plan.getPlanId());
            plan.setOverdueDays(0);
        }
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        syncOverdueStatus();
        FulfillmentPlan saved = planMapper.selectById(planId);
        boolean delayRequest = false;
        writeProgressLog(delayRequest ? "延期申请" : "更新",
                saved.getPlanId(), saved.getContractId(), originalStatus, saved.getStatus(),
                before, planSnapshot(saved), delayRequest ? plan.getDelayReason() : saved.getRemark(), clientIp);
        writePlanLog(delayRequest ? "FULFILLMENT_PLAN_DELAY_REQUEST" : "FULFILLMENT_PLAN_UPDATE",
                planId, before, planSnapshot(saved), "SUCCESS");
        return toPlanVo(saved);
    }

    @Transactional
    public void deletePlan(Long planId) {
        deletePlan(planId, null);
    }

    @Transactional
    public void deletePlan(Long planId, String clientIp) {
        ensureSchemaReady();
        FulfillmentPlan plan = assertPlanMaintainAccess(planId);
        String before = planSnapshot(plan);
        reminderMapper.delete(new LambdaQueryWrapper<ReminderRecord>()
                .eq(ReminderRecord::getPlanId, plan.getPlanId()));
        writeProgressLog("删除", plan.getPlanId(), plan.getContractId(), plan.getStatus(), "已删除",
                before, "{\"deleted\":true}", plan.getRemark(), clientIp);
        planMapper.deleteById(plan.getPlanId());
        writePlanLog("FULFILLMENT_PLAN_DELETE", plan.getPlanId(), before, "{\"deleted\":true}", "SUCCESS");
    }

    @Transactional
    public FulfillmentPlanVO confirmDelay(Long planId) {
        return confirmDelay(planId, null);
    }

    @Transactional
    public FulfillmentPlanVO confirmDelay(Long planId, String clientIp) {
        ensureSchemaReady();
        if (!canConfirmDelay()) {
            throw new SecurityException("仅部门主管或管理员可确认延期申请");
        }
        FulfillmentPlan plan = assertPlanDeptAuditAccess(planId);
        FulfillmentDelayApproval approval = latestPendingDelayApproval(plan.getPlanId());
        if (approval != null) {
            reviewDelayApproval(approval.getApprovalId(), new DelayApprovalDecisionRequest(true, "approved"), clientIp);
            return toPlanVo(planMapper.selectById(planId));
        }
        String before = planSnapshot(plan);
        String originalStatus = plan.getStatus();
        if (!"PENDING".equals(plan.getDelayStatus()) || plan.getDelayRequestedDueDate() == null) {
            throw new IllegalStateException("当前节点没有待确认的延期申请");
        }
        plan.setDueDate(plan.getDelayRequestedDueDate());
        plan.setDelayStatus("APPROVED");
        plan.setDelayConfirmedBy(StringUtils.hasText(SecurityContext.username()) ? SecurityContext.username() : SecurityContext.roleCode());
        plan.setDelayConfirmedAt(LocalDateTime.now());
        plan.setOverdueDays(0);
        if (!CLOSED_STATUS.contains(normalizeStatus(plan.getStatus()))) {
            plan.setStatus(plan.getProgress() != null && plan.getProgress() > 0 ? STATUS_IN_PROGRESS : STATUS_NOT_STARTED);
        }
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        closeOpenOverdueHistory(plan.getPlanId());
        syncOverdueStatus();
        FulfillmentPlan saved = planMapper.selectById(planId);
        writeProgressLog("延期确认", saved.getPlanId(), saved.getContractId(), originalStatus, saved.getStatus(),
                before, planSnapshot(saved), saved.getDelayReason(), clientIp);
        writePlanLog("FULFILLMENT_PLAN_DELAY_CONFIRM", planId, before, planSnapshot(saved), "SUCCESS");
        return toPlanVo(saved);
    }

    @Transactional
    public FulfillmentPlanVO handleOverdue(Long planId) {
        return handleOverdue(planId, null);
    }

    @Transactional
    public FulfillmentPlanVO handleOverdue(Long planId, String clientIp) {
        if (!Boolean.getBoolean("fulfillment.legacyOverdueClose")) {
            return handleOverdue(planId, null, clientIp);
        }
        ensureSchemaReady();
        FulfillmentPlan plan = assertPlanMaintainAccess(planId);
        String before = planSnapshot(plan);
        String originalStatus = plan.getStatus();
        plan.setStatus(STATUS_CLOSED);
        plan.setOverdueDays(0);
        plan.setHandledAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        closeOpenOverdueHistory(plan.getPlanId());
        FulfillmentPlan saved = planMapper.selectById(planId);
        writeProgressLog("逾期关闭", saved.getPlanId(), saved.getContractId(), originalStatus, saved.getStatus(),
                before, planSnapshot(saved), saved.getRemark(), clientIp);
        writePlanLog("FULFILLMENT_PLAN_OVERDUE_HANDLE", planId, before, planSnapshot(saved), "SUCCESS");
        return toPlanVo(saved);
    }

    @Transactional
    public FulfillmentPlanVO handleOverdue(Long planId, OverdueHandleRequest request, String clientIp) {
        ensureSchemaReady();
        syncOverdueStatus();
        FulfillmentPlan plan = assertPlanMaintainAccess(planId);
        if (request == null || !StringUtils.hasText(request.action())) {
            throw new IllegalArgumentException("请选择逾期处置方式");
        }
        String action = request.action().trim().toUpperCase();
        if (!STATUS_OVERDUE.equals(normalizeStatus(plan.getStatus()))) {
            throw new IllegalStateException("当前节点不是逾期状态，无需逾期处置");
        }
        if ("COMPLETE".equals(action) || "COMPLETED".equals(action)) {
            return completeOverduePlan(plan, request, clientIp);
        }
        if ("DELAY".equals(action) || "EXTEND".equals(action)) {
            return requestOverdueDelay(plan, request, clientIp);
        }
        throw new IllegalArgumentException("不支持的逾期处置方式");
    }

    private FulfillmentPlanVO completeOverduePlan(FulfillmentPlan plan,
                                                  OverdueHandleRequest request,
                                                  String clientIp) {
        String before = planSnapshot(plan);
        String originalStatus = plan.getStatus();
        String disposalRemark = requireText(request.disposalRemark(), "请填写处置说明");
        LocalDate actualCompletedDate = request.actualCompletedDate();
        if (actualCompletedDate == null) {
            throw new IllegalArgumentException("标记完成时必须填写实际完成日期");
        }
        if (actualCompletedDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("实际完成日期不能晚于今天");
        }
        assertCompletionVoucherReady(plan);
        plan.setStatus(STATUS_COMPLETED);
        plan.setProgress(100);
        plan.setActualCompletedDate(actualCompletedDate);
        plan.setOverdueDays(0);
        plan.setDelayStatus("NONE");
        plan.setHandledAt(LocalDateTime.now());
        plan.setRemark(disposalRemark);
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        resolveOpenOverdueHistory(plan.getPlanId(), "COMPLETED", disposalRemark, actualCompletedDate, null);
        FulfillmentPlan saved = planMapper.selectById(plan.getPlanId());
        writeProgressLog("OVERDUE_COMPLETE", saved.getPlanId(), saved.getContractId(), originalStatus, saved.getStatus(),
                before, planSnapshot(saved), disposalRemark, clientIp);
        writePlanLog("FULFILLMENT_PLAN_OVERDUE_COMPLETE", saved.getPlanId(), before, planSnapshot(saved), "SUCCESS");
        return toPlanVo(saved);
    }

    private FulfillmentPlanVO requestOverdueDelay(FulfillmentPlan plan,
                                                  OverdueHandleRequest request,
                                                  String clientIp) {
        String before = planSnapshot(plan);
        String originalStatus = plan.getStatus();
        String disposalRemark = requireText(request.disposalRemark(), "请填写处置说明");
        String delayReason = requireText(request.delayReason(), "请填写延期原因");
        LocalDate requestedDueDate = request.newPlannedDate();
        validateDelayRequestedDate(plan, requestedDueDate);
        requestDelay(plan, requestedDueDate, delayReason);
        plan.setStatus(STATUS_OVERDUE);
        plan.setRemark(disposalRemark);
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        FulfillmentDelayApproval approval = createDelayApproval(plan, requestedDueDate, delayReason);
        attachDelayApprovalToOpenOverdueHistory(plan, approval != null ? approval.getApprovalId() : null, disposalRemark);
        FulfillmentPlan saved = planMapper.selectById(plan.getPlanId());
        writeProgressLog("OVERDUE_DELAY_REQUEST", saved.getPlanId(), saved.getContractId(), originalStatus, saved.getStatus(),
                before, planSnapshot(saved), delayReason, clientIp);
        writePlanLog("FULFILLMENT_PLAN_OVERDUE_DELAY_REQUEST", saved.getPlanId(), before, planSnapshot(saved), "SUCCESS");
        return toPlanVo(saved);
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
            if (!isReminderWarningLevel(vo.warningLevel())) {
                continue;
            }
            if (reminderSuppressedForDelay(vo)) {
                continue;
            }
            ContractMain contract = contractMapper.selectById(vo.contractId());
            for (ReminderDelivery delivery : reminderDeliveries(vo, contract)) {
                if (reminderAlreadySent(vo, today, delivery.channel())) {
                    continue;
                }
                ReminderRecord record = new ReminderRecord();
                record.setPlanId(vo.planId());
                record.setContractId(vo.contractId());
                record.setReminderLevel(vo.warningLevel());
                record.setReminderDate(today);
                record.setChannel(delivery.channel());
                record.setReceiver(delivery.receiver());
                record.setContent(reminderContent(vo));
                record.setSendStatus("SENT");
                record.setSentAt(LocalDateTime.now());
                record.setCreatedAt(LocalDateTime.now());
                record.setUpdatedAt(LocalDateTime.now());
                record.setDeleted(0);
                reminderMapper.insert(record);
            }
        }
        return listReminders(contractId);
    }

    private boolean reminderSuppressedForDelay(FulfillmentPlanVO plan) {
        return plan != null && "PENDING".equals(plan.delayStatus());
    }

    private boolean isReminderWarningLevel(String warningLevel) {
        return REMINDER_WARNING_LEVELS.contains(warningLevel);
    }

    public List<ReminderRecordVO> listReminders(Long contractId) {
        ensureSchemaReady();
        if (contractId != null) {
            contractService.assertCanAccess(contractId);
        }
        List<ReminderRecord> records = reminderMapper.selectList(new LambdaQueryWrapper<ReminderRecord>()
                .eq(contractId != null, ReminderRecord::getContractId, contractId)
                .in(ReminderRecord::getReminderLevel, REMINDER_WARNING_LEVELS)
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
        long completed = plans.stream().filter(p -> STATUS_COMPLETED.equals(normalizeStatus(p.status()))).count();
        long handled = plans.stream().filter(p -> STATUS_CLOSED.equals(normalizeStatus(p.status()))).count();
        long reminderCount = listReminders(contractId).size();
        return new FulfillmentStats(total, warning, overdue, completed, handled, reminderCount);
    }

    public List<FulfillmentProgressLogVO> listProgressLogs(Long contractId, Long planId) {
        ensureSchemaReady();
        if (planId != null) {
            FulfillmentPlan plan = assertPlanAccess(planId);
            contractId = plan.getContractId();
        } else if (contractId != null) {
            contractService.assertCanAccess(contractId);
        }
        LambdaQueryWrapper<FulfillmentProgressLog> wrapper = new LambdaQueryWrapper<FulfillmentProgressLog>()
                .eq(contractId != null, FulfillmentProgressLog::getContractId, contractId)
                .eq(planId != null, FulfillmentProgressLog::getPlanId, planId)
                .orderByDesc(FulfillmentProgressLog::getOperateTime)
                .last("LIMIT 100");
        return progressLogMapper.selectList(wrapper).stream()
                .filter(log -> contractService.canAccess(contractMapper.selectById(log.getContractId())))
                .map(this::toProgressLogVo)
                .toList();
    }

    public List<FulfillmentVoucherVO> listVouchers(Long contractId, Long planId) {
        ensureSchemaReady();
        if (planId != null) {
            FulfillmentPlan plan = assertPlanAccess(planId);
            contractId = plan.getContractId();
        } else if (contractId != null) {
            contractService.assertCanAccess(contractId);
        }
        LambdaQueryWrapper<FulfillmentVoucher> wrapper = new LambdaQueryWrapper<FulfillmentVoucher>()
                .eq(contractId != null, FulfillmentVoucher::getContractId, contractId)
                .eq(planId != null, FulfillmentVoucher::getPlanId, planId)
                .orderByDesc(FulfillmentVoucher::getUploadedAt)
                .orderByDesc(FulfillmentVoucher::getVoucherId);
        return voucherMapper.selectList(wrapper).stream()
                .filter(voucher -> contractService.canAccess(contractMapper.selectById(voucher.getContractId())))
                .map(this::toVoucherVo)
                .toList();
    }

    public List<FulfillmentDelayApprovalVO> listDelayApprovals(Long contractId, Long planId, String status) {
        ensureSchemaReady();
        if (delayApprovalMapper == null) {
            return List.of();
        }
        if (planId != null) {
            FulfillmentPlan plan = assertPlanAccess(planId);
            contractId = plan.getContractId();
        } else if (contractId != null) {
            contractService.assertCanAccess(contractId);
        }
        LambdaQueryWrapper<FulfillmentDelayApproval> wrapper = new LambdaQueryWrapper<FulfillmentDelayApproval>()
                .eq(contractId != null, FulfillmentDelayApproval::getContractId, contractId)
                .eq(planId != null, FulfillmentDelayApproval::getPlanId, planId)
                .eq(StringUtils.hasText(status), FulfillmentDelayApproval::getStatus, status)
                .orderByDesc(FulfillmentDelayApproval::getRequestedAt)
                .last("LIMIT 100");
        return delayApprovalMapper.selectList(wrapper).stream()
                .filter(approval -> contractService.canAccess(contractMapper.selectById(approval.getContractId())))
                .map(this::toDelayApprovalVo)
                .toList();
    }

    @Transactional
    public FulfillmentDelayApprovalVO reviewDelayApproval(Long approvalId,
                                                          DelayApprovalDecisionRequest request,
                                                          String clientIp) {
        ensureSchemaReady();
        if (request == null || request.approved() == null) {
            throw new IllegalArgumentException("请选择审批结果");
        }
        FulfillmentDelayApproval approval = requireDelayApproval(approvalId);
        FulfillmentPlan plan = assertPlanDeptAuditAccess(approval.getPlanId());
        if (!"PENDING".equals(approval.getStatus())) {
            throw new IllegalStateException("该延期申请已处理");
        }
        String before = planSnapshot(plan);
        String originalStatus = plan.getStatus();
        LocalDateTime now = LocalDateTime.now();
        approval.setApproverId(SecurityContext.userId());
        approval.setApproverName(StringUtils.hasText(SecurityContext.username()) ? SecurityContext.username() : SecurityContext.roleCode());
        approval.setApprovedAt(now);
        approval.setUpdatedAt(now);
        if (Boolean.TRUE.equals(request.approved())) {
            approval.setStatus("APPROVED");
            plan.setDueDate(approval.getRequestedDueDate());
            plan.setDelayStatus("APPROVED");
            plan.setDelayConfirmedBy(approval.getApproverName());
            plan.setDelayConfirmedAt(now);
            plan.setDelayRejectedBy(null);
            plan.setDelayRejectedAt(null);
            plan.setDelayRejectReason(null);
            plan.setOverdueDays(0);
            if (!CLOSED_STATUS.contains(normalizeStatus(plan.getStatus()))) {
                plan.setStatus(plan.getProgress() != null && plan.getProgress() > 0 ? STATUS_IN_PROGRESS : STATUS_NOT_STARTED);
            }
            plan.setUpdatedAt(now);
            planMapper.updateById(plan);
            delayApprovalMapper.updateById(approval);
            resolveOpenOverdueHistory(plan.getPlanId(), "DELAY_APPROVED", approval.getDelayReason(), null, approval.getApprovalId());
        } else {
            String rejectReason = requireText(request.remark(), "请填写驳回原因");
            approval.setStatus("REJECTED");
            approval.setRejectReason(rejectReason);
            plan.setStatus(STATUS_OVERDUE);
            plan.setDelayStatus("REJECTED");
            plan.setDelayRejectedBy(approval.getApproverName());
            plan.setDelayRejectedAt(now);
            plan.setDelayRejectReason(rejectReason);
            plan.setUpdatedAt(now);
            planMapper.updateById(plan);
            delayApprovalMapper.updateById(approval);
            int overdueDays = plan.getDueDate() == null ? 0 : (int) Math.max(0, ChronoUnit.DAYS.between(plan.getDueDate(), LocalDate.now()));
            upsertOpenOverdueHistory(plan, overdueDays);
        }
        syncOverdueStatus();
        FulfillmentPlan saved = planMapper.selectById(plan.getPlanId());
        FulfillmentDelayApproval savedApproval = delayApprovalMapper.selectById(approval.getApprovalId());
        writeProgressLog(Boolean.TRUE.equals(request.approved()) ? "DELAY_APPROVE" : "DELAY_REJECT",
                saved.getPlanId(), saved.getContractId(), originalStatus, saved.getStatus(),
                before, planSnapshot(saved), Boolean.TRUE.equals(request.approved()) ? approval.getDelayReason() : approval.getRejectReason(), clientIp);
        writePlanLog(Boolean.TRUE.equals(request.approved()) ? "FULFILLMENT_DELAY_APPROVE" : "FULFILLMENT_DELAY_REJECT",
                saved.getPlanId(), before, planSnapshot(saved), "SUCCESS");
        return toDelayApprovalVo(savedApproval);
    }

    @Transactional
    public FulfillmentVoucherVO uploadVoucher(Long planId,
                                              MultipartFile file,
                                              String voucherType,
                                              String remark,
                                              String clientIp) throws Exception {
        ensureSchemaReady();
        FulfillmentPlan plan = assertPlanMaintainAccess(planId);
        validateVoucherFile(file);
        FileStorageService.StoredFile stored = fileStorageService.store(file);
        FileInfo fileInfo = findOrCreateFile(stored, file.getOriginalFilename());
        LocalDateTime now = LocalDateTime.now();
        FulfillmentVoucher voucher = new FulfillmentVoucher();
        voucher.setPlanId(plan.getPlanId());
        voucher.setContractId(plan.getContractId());
        voucher.setFileId(fileInfo.getFileId());
        voucher.setVoucherType(StringUtils.hasText(voucherType) ? voucherType.trim().toUpperCase() : "PROGRESS");
        voucher.setReviewStatus("PAYMENT".equals(plan.getPlanType()) ? "PENDING_REVIEW" : "NOT_REQUIRED");
        voucher.setUploadedBy(SecurityContext.userId());
        voucher.setUploadedByName(SecurityContext.username());
        voucher.setUploadedAt(now);
        voucher.setRemark(clip(remark, 500));
        voucher.setCreatedAt(now);
        voucher.setUpdatedAt(now);
        voucher.setDeleted(0);
        voucherMapper.insert(voucher);

        String after = voucherSnapshot(voucher, fileInfo);
        writeProgressLog("凭证上传", plan.getPlanId(), plan.getContractId(), plan.getStatus(), plan.getStatus(),
                null, after, voucher.getRemark(), clientIp);
        writePlanLog("FULFILLMENT_VOUCHER_UPLOAD", plan.getPlanId(), null, after, "SUCCESS");
        return toVoucherVo(voucherMapper.selectById(voucher.getVoucherId()));
    }

    @Transactional
    public FulfillmentVoucherVO reviewVoucher(Long voucherId,
                                              boolean approved,
                                              String remark,
                                              String clientIp) {
        ensureSchemaReady();
        FulfillmentVoucher voucher = requireVoucher(voucherId);
        FulfillmentPlan plan = requirePlan(voucher.getPlanId());
        assertVoucherReviewAccess(plan);
        String before = voucherSnapshot(voucher, requireFile(voucher.getFileId()));
        voucher.setReviewStatus(approved ? "APPROVED" : "REJECTED");
        voucher.setReviewerId(SecurityContext.userId());
        voucher.setReviewerName(SecurityContext.username());
        voucher.setReviewedAt(LocalDateTime.now());
        if (StringUtils.hasText(remark)) {
            voucher.setRemark(clip(remark, 500));
        }
        voucher.setUpdatedAt(LocalDateTime.now());
        voucherMapper.updateById(voucher);
        FulfillmentVoucher saved = voucherMapper.selectById(voucherId);
        String after = voucherSnapshot(saved, requireFile(saved.getFileId()));
        writeProgressLog("凭证审核", plan.getPlanId(), plan.getContractId(), plan.getStatus(), plan.getStatus(),
                before, after, saved.getRemark(), clientIp);
        writePlanLog("FULFILLMENT_VOUCHER_REVIEW", plan.getPlanId(), before, after, "SUCCESS");
        return toVoucherVo(saved);
    }

    public ResponseEntity<Resource> downloadVoucher(Long voucherId) {
        ensureSchemaReady();
        FulfillmentVoucher voucher = requireVoucher(voucherId);
        assertPlanAccess(voucher.getPlanId());
        FileInfo fileInfo = requireFile(voucher.getFileId());
        Path path = fileStorageService.resolve(fileInfo.getObjectKey());
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(fileInfo.getFileName()))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    public List<DeliverableVO> listDeliverables(Long contractId) {
        return listDeliverables(contractId, null);
    }

    public List<DeliverableVO> listDeliverables(Long contractId, Long planId) {
        ensureSchemaReady();
        if (planId != null) {
            FulfillmentPlan plan = assertPlanAccess(planId);
            contractId = plan.getContractId();
        }
        LambdaQueryWrapper<FulfillmentDeliverable> wrapper = new LambdaQueryWrapper<FulfillmentDeliverable>()
                .eq(contractId != null, FulfillmentDeliverable::getContractId, contractId)
                .eq(planId != null, FulfillmentDeliverable::getPlanId, planId)
                .orderByAsc(FulfillmentDeliverable::getDeliverableId);
        return toDeliverableVos(deliverableMapper.selectList(wrapper));
    }

    @Transactional
    public DeliverableVO createDeliverable(DeliverableRequest request, String clientIp) {
        ensureSchemaReady();
        if (request == null || request.contractId() == null) {
            throw new IllegalArgumentException("contractId is required");
        }
        contractService.assertCanAccess(request.contractId());
        FulfillmentDeliverable item = new FulfillmentDeliverable();
        applyDeliverableRequest(item, request, true);
        LocalDateTime now = LocalDateTime.now();
        item.setStatus(DELIVERABLE_PENDING_SUBMIT);
        applyConfirm(item, false);
        item.setSubmissionVersion(0);
        item.setAiExtracted(0);
        item.setAiConfidence(null);
        item.setSourceClause(null);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        item.setDeleted(0);
        deliverableMapper.insert(item);
        writeDeliverableLog("FULFILLMENT_DELIVERABLE_CREATE", item, null, deliverableSnapshot(item), clientIp);
        return toDeliverableVo(deliverableMapper.selectById(item.getDeliverableId()));
    }

    @Transactional
    public DeliverableVO updateDeliverable(Long deliverableId, DeliverableRequest request) {
        return updateDeliverable(deliverableId, request, null);
    }

    @Transactional
    public DeliverableVO updateDeliverable(Long deliverableId, DeliverableRequest request, String clientIp) {
        ensureSchemaReady();
        FulfillmentDeliverable item = assertDeliverableMaintainAccess(deliverableId);
        assertDeliverableEditable(item);
        String before = deliverableSnapshot(item);
        applyDeliverableRequest(item, request, false);
        item.setUpdatedAt(LocalDateTime.now());
        deliverableMapper.updateById(item);
        FulfillmentDeliverable saved = deliverableMapper.selectById(deliverableId);
        writeDeliverableLog("FULFILLMENT_DELIVERABLE_UPDATE", saved, before, deliverableSnapshot(saved), clientIp);
        return toDeliverableVo(saved);
    }

    @Transactional
    public DeliverableVO transitionDeliverable(Long deliverableId,
                                               DeliverableTransitionRequest request,
                                               String clientIp) {
        ensureSchemaReady();
        if (request == null || !StringUtils.hasText(request.action())) {
            throw new IllegalArgumentException("请选择交付物操作");
        }
        FulfillmentDeliverable item = assertDeliverableAccess(deliverableId);
        String action = request.action().trim().toUpperCase();
        String before = deliverableSnapshot(item);
        String operation;
        switch (action) {
            case "SUBMIT", "RESUBMIT" -> {
                assertDeliverableMaintainAccess(deliverableId);
                transitionDeliverableToSubmitted(item);
                operation = "FULFILLMENT_DELIVERABLE_SUBMIT";
            }
            case "SUPPLEMENT", "NEED_SUPPLEMENT" -> {
                assertDeliverableReviewAccess(item);
                transitionDeliverableReview(item, DELIVERABLE_NEED_SUPPLEMENT,
                        requireText(request.remark(), "请填写需要补充的内容"));
                operation = "FULFILLMENT_DELIVERABLE_SUPPLEMENT";
            }
            case "REJECT", "REJECTED" -> {
                assertDeliverableReviewAccess(item);
                transitionDeliverableReview(item, DELIVERABLE_REJECTED,
                        requireText(request.remark(), "请填写驳回原因"));
                operation = "FULFILLMENT_DELIVERABLE_REJECT";
            }
            case "ACCEPT", "ACCEPTED", "APPROVE" -> {
                assertDeliverableReviewAccess(item);
                transitionDeliverableAccepted(item, request.remark());
                operation = "FULFILLMENT_DELIVERABLE_ACCEPT";
            }
            case "ACCEPTANCE", "ACCEPTANCE_PASSED" -> {
                assertDeliverableReviewAccess(item);
                transitionDeliverableAcceptancePassed(item, request.remark());
                operation = "FULFILLMENT_DELIVERABLE_ACCEPTANCE";
            }
            default -> throw new IllegalArgumentException("不支持的交付物操作");
        }
        item.setUpdatedAt(LocalDateTime.now());
        deliverableMapper.updateById(item);
        syncLinkedPlanFromDeliverable(item, clientIp);
        FulfillmentDeliverable saved = deliverableMapper.selectById(deliverableId);
        writeDeliverableLog(operation, saved, before, deliverableSnapshot(saved), clientIp);
        return toDeliverableVo(saved);
    }

    @Transactional
    public DeliverableVO confirmDeliverable(Long deliverableId, boolean confirmed) {
        return confirmDeliverable(deliverableId, confirmed, null);
    }

    @Transactional
    public DeliverableVO confirmDeliverable(Long deliverableId, boolean confirmed, String clientIp) {
        if (!confirmed) {
            throw new IllegalStateException("已提交的交付物不能直接取消通过，请使用补充或驳回流程");
        }
        return transitionDeliverable(deliverableId, new DeliverableTransitionRequest("ACCEPT", ""), clientIp);
    }

    @Transactional
    public void deleteDeliverable(Long deliverableId, String clientIp) {
        ensureSchemaReady();
        FulfillmentDeliverable item = assertDeliverableMaintainAccess(deliverableId);
        assertDeliverableEditable(item);
        String before = deliverableSnapshot(item);
        item.setDeleted(1);
        item.setUpdatedAt(LocalDateTime.now());
        deliverableMapper.updateById(item);
        writeDeliverableLog("FULFILLMENT_DELIVERABLE_DELETE", item, before, deliverableSnapshot(item), clientIp);
    }

    @Transactional
    public DeliverableVO uploadDeliverableFile(Long deliverableId,
                                               MultipartFile file,
                                               String remark,
                                               String clientIp) throws Exception {
        ensureSchemaReady();
        FulfillmentDeliverable item = assertDeliverableMaintainAccess(deliverableId);
        assertDeliverableEditable(item);
        validateVoucherFile(file);
        String before = deliverableSnapshot(item);
        FileStorageService.StoredFile stored = fileStorageService.store(file);
        FileInfo fileInfo = findOrCreateFile(stored, file.getOriginalFilename());
        item.setFileId(fileInfo.getFileId());
        if (StringUtils.hasText(remark)) {
            item.setRemark(clip(remark, 500));
        }
        item.setUpdatedAt(LocalDateTime.now());
        deliverableMapper.updateById(item);
        FulfillmentDeliverable saved = deliverableMapper.selectById(deliverableId);
        writeDeliverableLog("FULFILLMENT_DELIVERABLE_FILE_UPLOAD", saved, before, deliverableSnapshot(saved), clientIp);
        return toDeliverableVo(saved);
    }

    public ResponseEntity<Resource> downloadDeliverableFile(Long deliverableId) {
        ensureSchemaReady();
        FulfillmentDeliverable item = assertDeliverableAccess(deliverableId);
        if (item.getFileId() == null) {
            throw new IllegalArgumentException("deliverable file not found");
        }
        FileInfo fileInfo = requireFile(item.getFileId());
        Path path = fileStorageService.resolve(fileInfo.getObjectKey());
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(fileInfo.getFileName()))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
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
        syncPaymentPlanStatusFields(plan);
        paymentPlanMapper.insert(plan);
        writeOperationLog("PAYMENT_PLAN_CREATE", "PAYMENT_PLAN", plan.getPaymentPlanId(),
                null, paymentPlanSnapshot(plan), "SUCCESS");
        syncPaymentPlanStatus(plan);
        return toPaymentPlanVo(paymentPlanMapper.selectById(plan.getPaymentPlanId()));
    }

    @Transactional
    public PaymentPlanVO updatePaymentPlan(Long paymentPlanId, PaymentPlanRequest request) {
        ensureSchemaReady();
        PaymentPlan plan = assertPaymentPlanAccess(paymentPlanId);
        String before = paymentPlanSnapshot(plan);
        applyPaymentPlanRequest(plan, request);
        validatePaymentPercentage(plan);
        plan.setUpdatedAt(LocalDateTime.now());
        syncPaymentPlanStatusFields(plan);
        paymentPlanMapper.updateById(plan);
        writeOperationLog("PAYMENT_PLAN_UPDATE", "PAYMENT_PLAN", plan.getPaymentPlanId(),
                before, paymentPlanSnapshot(plan), "SUCCESS");
        syncPaymentPlanStatus(plan);
        return toPaymentPlanVo(paymentPlanMapper.selectById(paymentPlanId));
    }

    @Transactional
    public void deletePaymentPlan(Long paymentPlanId) {
        ensureSchemaReady();
        PaymentPlan plan = assertPaymentPlanAccess(paymentPlanId);
        long paymentRecordCount = paymentRecordMapper.selectCount(
                new LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getPaymentPlanId, paymentPlanId));
        Long invoiceCountValue = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM invoice_record
                WHERE payment_plan_id = ?
                  AND is_deleted = 0
                """, Long.class, paymentPlanId);
        long invoiceCount = invoiceCountValue == null ? 0L : invoiceCountValue;
        assertPaymentPlanDeletable(paymentRecordCount, invoiceCount);

        String before = paymentPlanSnapshot(plan);
        paymentPlanMapper.deleteById(paymentPlanId);
        writeOperationLog("PAYMENT_PLAN_DELETE", "PAYMENT_PLAN", paymentPlanId,
                before, "{\"deleted\":true}", "SUCCESS");
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
        if (request == null) {
            throw new IllegalArgumentException("payment record request is required");
        }
        syncPaymentPlanStatus(plan);
        plan = paymentPlanMapper.selectById(paymentPlanId);
        if ("SUSPENDED".equals(plan.getStatus())) {
            throw new IllegalStateException("付款计划已挂起，不能登记付款");
        }
        if (!paymentConditionMet(plan)) {
            throw new IllegalStateException("付款条件未满足，只能查看、备注或补充通知，不能登记付款");
        }
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
        if (record.getPaidDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("实际付款日期不能晚于今天");
        }
        record.setBankSerialNo(StringUtils.hasText(request.bankSerialNo()) ? request.bankSerialNo().trim() : "");
        record.setHandlerName(StringUtils.hasText(request.handlerName()) ? request.handlerName().trim() : currentUserLabel());
        record.setVoucherFileId(request.voucherFileId());
        record.setPayer(StringUtils.hasText(request.payer()) ? request.payer().trim() : "甲方");
        record.setReceiver(StringUtils.hasText(request.receiver()) ? request.receiver().trim() : Objects.toString(plan.getPayee(), "乙方"));
        record.setRemark(StringUtils.hasText(request.remark()) ? request.remark().trim() : "");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        record.setDeleted(0);
        paymentRecordMapper.insert(record);
        writeOperationLog("PAYMENT_RECORD_CREATE", "PAYMENT_RECORD", record.getPaymentRecordId(),
                null, paymentRecordSnapshot(record), "SUCCESS");
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
        String before = paymentRecordSnapshot(record);
        paymentRecordMapper.deleteById(recordId);
        writeOperationLog("PAYMENT_RECORD_DELETE", "PAYMENT_RECORD", recordId,
                before, null, "SUCCESS");
        if (plan != null) {
            syncPaymentPlanStatus(plan);
        }
    }

    @Transactional
    public PaymentRecordVO uploadPaymentVoucher(Long recordId, MultipartFile file, String remark) throws Exception {
        ensureSchemaReady();
        PaymentRecord record = paymentRecordMapper.selectById(recordId);
        if (record == null) {
            throw new IllegalArgumentException("payment record not found");
        }
        contractService.assertCanAccess(record.getContractId());
        validateVoucherFile(file);
        String before = paymentRecordSnapshot(record);
        FileStorageService.StoredFile stored = fileStorageService.store(file);
        FileInfo fileInfo = findOrCreateFile(stored, file.getOriginalFilename());
        record.setVoucherFileId(fileInfo.getFileId());
        if (StringUtils.hasText(remark)) {
            record.setRemark(clip(remark, 500));
        }
        record.setUpdatedAt(LocalDateTime.now());
        paymentRecordMapper.updateById(record);
        PaymentRecord saved = paymentRecordMapper.selectById(recordId);
        writeOperationLog("PAYMENT_RECORD_VOUCHER_UPLOAD", "PAYMENT_RECORD", recordId,
                before, paymentRecordSnapshot(saved), "SUCCESS");
        return toPaymentRecordVo(saved);
    }

    public ResponseEntity<Resource> downloadPaymentVoucher(Long recordId) {
        ensureSchemaReady();
        PaymentRecord record = paymentRecordMapper.selectById(recordId);
        if (record == null || record.getVoucherFileId() == null) {
            throw new IllegalArgumentException("payment voucher not found");
        }
        contractService.assertCanAccess(record.getContractId());
        FileInfo fileInfo = requireFile(record.getVoucherFileId());
        Path path = fileStorageService.resolve(fileInfo.getObjectKey());
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(fileInfo.getFileName()))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    public List<InvoiceRecordVO> listInvoices(Long contractId, Long paymentPlanId) {
        ensureSchemaReady();
        if (paymentPlanId != null) {
            PaymentPlan plan = assertPaymentPlanAccess(paymentPlanId);
            contractId = plan.getContractId();
        } else if (contractId != null) {
            contractService.assertCanAccess(contractId);
        }
        StringBuilder sql = new StringBuilder("""
                SELECT i.invoice_id, i.payment_plan_id, i.contract_id, i.invoice_no, i.invoice_amount,
                       i.invoice_date, i.invoice_status, i.file_id, i.remark, i.created_at,
                       c.title AS contract_title, p.phase_name, f.file_name
                FROM invoice_record i
                LEFT JOIN contract_main c ON c.contract_id = i.contract_id
                LEFT JOIN payment_plan p ON p.payment_plan_id = i.payment_plan_id
                LEFT JOIN file_info f ON f.file_id = i.file_id
                WHERE i.is_deleted = 0
                """);
        List<Object> args = new ArrayList<>();
        if (contractId != null) {
            sql.append(" AND i.contract_id = ?");
            args.add(contractId);
        }
        if (paymentPlanId != null) {
            sql.append(" AND i.payment_plan_id = ?");
            args.add(paymentPlanId);
        }
        sql.append(" ORDER BY i.invoice_date DESC, i.invoice_id DESC LIMIT 100");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new InvoiceRecordVO(
                rs.getLong("invoice_id"),
                rs.getLong("payment_plan_id"),
                rs.getLong("contract_id"),
                Objects.toString(rs.getString("contract_title"), ""),
                Objects.toString(rs.getString("phase_name"), ""),
                rs.getString("invoice_no"),
                money(rs.getBigDecimal("invoice_amount")),
                rs.getObject("invoice_date", LocalDate.class),
                rs.getString("invoice_status"),
                rs.getObject("file_id") == null ? null : rs.getLong("file_id"),
                rs.getString("file_name"),
                rs.getObject("file_id") == null ? "" : "/api/fulfillment/payments/invoices/" + rs.getLong("invoice_id") + "/file",
                rs.getString("remark"),
                rs.getObject("created_at", LocalDateTime.class)
        ), args.toArray()).stream()
                .filter(item -> contractService.canAccess(contractMapper.selectById(item.contractId())))
                .toList();
    }

    @Transactional
    public InvoiceRecordVO createInvoice(Long paymentPlanId, InvoiceRecordRequest request) {
        ensureSchemaReady();
        PaymentPlan plan = assertPaymentPlanAccess(paymentPlanId);
        if (request == null || !StringUtils.hasText(request.invoiceNo())) {
            throw new IllegalArgumentException("invoiceNo is required");
        }
        jdbcTemplate.update("""
                INSERT INTO invoice_record
                    (payment_plan_id, contract_id, invoice_no, invoice_amount, invoice_date, invoice_status, file_id, remark, created_at, updated_at, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """,
                paymentPlanId,
                plan.getContractId(),
                request.invoiceNo().trim(),
                money(request.invoiceAmount()),
                request.invoiceDate() != null ? request.invoiceDate() : LocalDate.now(),
                normalizeInvoiceStatus(request.invoiceStatus()),
                request.fileId(),
                clip(request.remark(), 500),
                LocalDateTime.now(),
                LocalDateTime.now());
        syncPaymentPlanStatus(plan);
        writeOperationLog("INVOICE_RECORD_CREATE", "PAYMENT_PLAN", paymentPlanId,
                null, "invoiceNo=" + request.invoiceNo(), "SUCCESS");
        return listInvoices(null, paymentPlanId).stream().findFirst().orElseThrow();
    }

    @Transactional
    public InvoiceRecordVO uploadInvoiceFile(Long invoiceId, MultipartFile file, String remark) throws Exception {
        ensureSchemaReady();
        validateVoucherFile(file);
        Long contractId = jdbcTemplate.queryForObject("""
                SELECT contract_id FROM invoice_record WHERE invoice_id = ? AND is_deleted = 0
                """, Long.class, invoiceId);
        if (contractId == null) {
            throw new IllegalArgumentException("invoice not found");
        }
        contractService.assertCanAccess(contractId);
        FileStorageService.StoredFile stored = fileStorageService.store(file);
        FileInfo fileInfo = findOrCreateFile(stored, file.getOriginalFilename());
        jdbcTemplate.update("""
                UPDATE invoice_record
                SET file_id = ?,
                    remark = CASE WHEN ? = '' THEN remark ELSE ? END,
                    updated_at = ?
                WHERE invoice_id = ? AND is_deleted = 0
                """, fileInfo.getFileId(), clip(remark, 500), clip(remark, 500), LocalDateTime.now(), invoiceId);
        writeOperationLog("INVOICE_FILE_UPLOAD", "INVOICE_RECORD", invoiceId,
                null, "fileId=" + fileInfo.getFileId(), "SUCCESS");
        return listInvoices(null, null).stream()
                .filter(item -> Objects.equals(item.invoiceId(), invoiceId))
                .findFirst()
                .orElseThrow();
    }

    public ResponseEntity<Resource> downloadInvoiceFile(Long invoiceId) {
        ensureSchemaReady();
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT contract_id, file_id FROM invoice_record WHERE invoice_id = ? AND is_deleted = 0
                """, invoiceId);
        contractService.assertCanAccess(((Number) row.get("contract_id")).longValue());
        Object fileIdValue = row.get("file_id");
        if (fileIdValue == null) {
            throw new IllegalArgumentException("invoice file not found");
        }
        FileInfo fileInfo = requireFile(((Number) fileIdValue).longValue());
        Path path = fileStorageService.resolve(fileInfo.getObjectKey());
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(fileInfo.getFileName()))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    private void createAiLinkedStandardDeliverables(Long contractId) {
        List<FulfillmentPlan> plans = planMapper.selectList(new LambdaQueryWrapper<FulfillmentPlan>()
                .eq(FulfillmentPlan::getContractId, contractId)
                .orderByAsc(FulfillmentPlan::getDueDate)
                .orderByAsc(FulfillmentPlan::getPlanId));
        List<FulfillmentDeliverable> items = plans.stream()
                .map(this::deliverableFromPlan)
                .filter(Objects::nonNull)
                .toList();
        if (items.isEmpty()) {
            items = fallbackDeliverables(contractId, plans);
        }
        for (FulfillmentDeliverable item : items) {
            FulfillmentDeliverable existing = deliverableMapper.selectOne(new LambdaQueryWrapper<FulfillmentDeliverable>()
                    .eq(FulfillmentDeliverable::getContractId, contractId)
                    .eq(FulfillmentDeliverable::getDeliverableName, item.getDeliverableName())
                    .last("LIMIT 1"));
            if (existing == null) {
                deliverableMapper.insert(item);
                continue;
            }
            if (existing.getPlanId() == null && item.getPlanId() != null) {
                existing.setPlanId(item.getPlanId());
                existing.setSourceClause(item.getSourceClause());
                existing.setAiConfidence(item.getAiConfidence());
                existing.setAiExtracted(item.getAiExtracted());
                existing.setConfirmStatus(item.getConfirmStatus());
                existing.setUpdatedAt(LocalDateTime.now());
                deliverableMapper.updateById(existing);
            }
        }
    }

    private List<FulfillmentDeliverable> fallbackDeliverables(Long contractId, List<FulfillmentPlan> plans) {
        return List.of(
                deliverable(contractId, findPlanByType(plans, "PREPARE"), "DESIGN_DOC", "\u9700\u6c42\u8bbe\u8ba1\u6587\u6863", "\u7b7e\u8ba2\u9636\u6bb5"),
                deliverable(contractId, findPlanByType(plans, "DELIVERY"), "SOURCE_CODE", "\u6e90\u4ee3\u7801", "\u4e2d\u671f\u4ea4\u4ed8"),
                deliverable(contractId, findPlanByType(plans, "DELIVERY"), "RUNNABLE_APP", "\u53ef\u8fd0\u884c\u7a0b\u5e8f", "\u4e2d\u671f\u4ea4\u4ed8"),
                deliverable(contractId, findPlanByType(plans, "ACCEPTANCE"), "ACCEPTANCE_REPORT", "\u9a8c\u6536\u62a5\u544a", "\u9a8c\u6536\u9636\u6bb5")
        );
    }

    private FulfillmentDeliverable deliverableFromPlan(FulfillmentPlan plan) {
        String planType = normalizePlanType(plan.getPlanType());
        String deliverableType = switch (planType) {
            case "PREPARE" -> "DESIGN_DOC";
            case "DELIVERY" -> "DELIVERY_ARTIFACT";
            case "ACCEPTANCE" -> "ACCEPTANCE_REPORT";
            case "INVOICE" -> "INVOICE";
            case "PAYMENT" -> "PAYMENT_VOUCHER";
            case "WARRANTY" -> "WARRANTY_RECORD";
            case "CHECK" -> "PROGRESS_REPORT";
            default -> null;
        };
        if (deliverableType == null) {
            return null;
        }
        String name = switch (planType) {
            case "PREPARE" -> "\u9700\u6c42\u8bbe\u8ba1\u6587\u6863";
            case "DELIVERY" -> deliverableNameFromPlan(plan, "\u4ea4\u4ed8\u6210\u679c\u6587\u4ef6");
            case "ACCEPTANCE" -> "\u9a8c\u6536\u62a5\u544a";
            case "INVOICE" -> "\u53d1\u7968";
            case "PAYMENT" -> "\u4ed8\u6b3e\u51ed\u8bc1";
            case "WARRANTY" -> "\u8d28\u4fdd\u670d\u52a1\u8bb0\u5f55";
            case "CHECK" -> "\u8fdb\u5ea6\u786e\u8ba4\u8bb0\u5f55";
            default -> "\u4ea4\u4ed8\u7269";
        };
        return deliverable(plan.getContractId(), plan, deliverableType, name,
                StringUtils.hasText(plan.getNodeName()) ? plan.getNodeName() : planType);
    }

    private String deliverableNameFromPlan(FulfillmentPlan plan, String fallback) {
        if (StringUtils.hasText(plan.getNodeName())) {
            String name = plan.getNodeName().trim();
            if (name.contains("\u4ea4\u4ed8") || name.contains("\u6587\u6863") || name.contains("\u62a5\u544a")
                    || name.contains("\u53d1\u7968") || name.contains("\u51ed\u8bc1")) {
                return clip(name, 120);
            }
        }
        return fallback;
    }

    private FulfillmentPlan findPlanByType(List<FulfillmentPlan> plans, String type) {
        return plans.stream()
                .filter(plan -> type.equals(normalizePlanType(plan.getPlanType())))
                .findFirst()
                .orElse(null);
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
        List<FulfillmentPlan> aiPaymentNodes = planMapper.selectList(new LambdaQueryWrapper<FulfillmentPlan>()
                .eq(FulfillmentPlan::getContractId, contract.getContractId())
                .eq(FulfillmentPlan::getSourceType, "AI")
                .eq(FulfillmentPlan::getPlanType, "PAYMENT"));
        if (!shouldCreateStandardPaymentPlans(aiPaymentNodes)) {
            return;
        }
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

    private boolean shouldCreateStandardPaymentPlans(List<FulfillmentPlan> aiPaymentNodes) {
        return aiPaymentNodes == null || aiPaymentNodes.isEmpty();
    }

    private void createPaymentPlansFromFulfillmentNodes(Long contractId) {
        ContractMain contract = contractMapper.selectById(contractId);
        if (contract == null) {
            return;
        }
        BigDecimal contractAmount = money(contract.getAmount());
        String contractText = resolveArchivedContractText(contractId);
        List<FulfillmentPlan> nodes = planMapper.selectList(new LambdaQueryWrapper<FulfillmentPlan>()
                .eq(FulfillmentPlan::getContractId, contractId)
                .in(FulfillmentPlan::getPlanType, List.of("PAYMENT", "INVOICE"))
                .orderByAsc(FulfillmentPlan::getDueDate)
                .orderByAsc(FulfillmentPlan::getPlanId));
        for (FulfillmentPlan node : nodes) {
            BigDecimal percentage = paymentPercentageFromNode(node);
            BigDecimal explicitAmount = paymentAmountFromNode(node, contractText);
            BigDecimal plannedAmount = explicitAmount.compareTo(BigDecimal.ZERO) > 0
                    ? explicitAmount
                    : percentage.compareTo(BigDecimal.ZERO) > 0
                            ? money(contractAmount.multiply(percentage).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP))
                            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (percentage.compareTo(BigDecimal.ZERO) == 0
                    && explicitAmount.compareTo(BigDecimal.ZERO) > 0
                    && contractAmount.compareTo(BigDecimal.ZERO) > 0) {
                percentage = explicitAmount.multiply(ONE_HUNDRED)
                        .divide(contractAmount, 2, RoundingMode.HALF_UP);
            }

            PaymentPlan existing = paymentPlanMapper.selectOne(new LambdaQueryWrapper<PaymentPlan>()
                    .eq(PaymentPlan::getFulfillmentPlanId, node.getPlanId()));
            if (existing != null) {
                backfillPaymentPlanAmount(existing, percentage, plannedAmount);
                continue;
            }
            PaymentPlan plan = new PaymentPlan();
            plan.setContractId(contractId);
            plan.setFulfillmentPlanId(node.getPlanId());
            plan.setPhaseName(StringUtils.hasText(node.getNodeName()) ? node.getNodeName() : "付款节点");
            plan.setPercentage(percentage);
            plan.setPlannedAmount(plannedAmount);
            plan.setDueDate(node.getDueDate() != null ? node.getDueDate() : LocalDate.now());
            plan.setPayee(contract.getCounterparty());
            plan.setPaymentCondition(paymentConditionFromNode(node));
            plan.setConditionType(paymentConditionType(node));
            plan.setConditionStatus("PENDING");
            plan.setPrerequisiteDelivery(prerequisiteFromPaymentNode(node));
            plan.setPenaltyRate(DEFAULT_PENALTY_RATE);
            plan.setStatus("READY_TO_PAY");
            plan.setRemark(clip(node.getSourceClause(), 500));
            plan.setCreatedAt(LocalDateTime.now());
            plan.setUpdatedAt(LocalDateTime.now());
            plan.setDeleted(0);
            syncPaymentPlanStatusFields(plan);
            paymentPlanMapper.insert(plan);
            writeOperationLog("PAYMENT_PLAN_FROM_FULFILLMENT_NODE", "PAYMENT_PLAN", plan.getPaymentPlanId(),
                    null, paymentPlanSnapshot(plan), "SUCCESS");
        }
    }

    private void backfillPaymentPlanAmount(PaymentPlan plan, BigDecimal percentage, BigDecimal plannedAmount) {
        if (plan == null || plannedAmount == null || plannedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal normalizedAmount = money(plannedAmount);
        BigDecimal currentAmount = money(plan.getPlannedAmount());
        if (currentAmount.compareTo(normalizedAmount) == 0) {
            return;
        }
        if (plan.getPaymentPlanId() != null
                && paidAmount(plan.getPaymentPlanId()).compareTo(BigDecimal.ZERO) > 0) {
            return;
        }
        plan.setPlannedAmount(normalizedAmount);
        if (percentage != null) {
            plan.setPercentage(percentage.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        }
        syncPaymentPlanStatusFields(plan);
        plan.setUpdatedAt(LocalDateTime.now());
        paymentPlanMapper.updateById(plan);
        writeOperationLog("PAYMENT_PLAN_AMOUNT_BACKFILL", "PAYMENT_PLAN", plan.getPaymentPlanId(),
                null, paymentPlanSnapshot(plan), "SUCCESS");
    }

    private BigDecimal paymentPercentageFromNode(FulfillmentPlan node) {
        String text = (Objects.toString(node.getNodeName(), "") + " " + Objects.toString(node.getSourceClause(), ""));
        Matcher matcher = Pattern.compile("(\\d{1,3}(?:\\.\\d{1,2})?)\\s*%").matcher(text);
        if (matcher.find()) {
            BigDecimal value = new BigDecimal(matcher.group(1));
            if (value.compareTo(BigDecimal.ZERO) >= 0 && value.compareTo(ONE_HUNDRED) <= 0) {
                return value.setScale(2, RoundingMode.HALF_UP);
            }
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal paymentAmountFromNode(FulfillmentPlan node, String contractText) {
        if (node == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        String directText = Objects.toString(node.getNodeName(), "") + " "
                + Objects.toString(node.getSourceClause(), "");
        BigDecimal directAmount = firstMoneyAmount(directText);
        if (directAmount.compareTo(BigDecimal.ZERO) > 0) {
            return directAmount;
        }
        if (!StringUtils.hasText(contractText)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        List<Integer> anchors = new ArrayList<>();
        addTextAnchors(anchors, contractText, node.getNodeName());
        addTextAnchors(anchors, contractText, node.getSourceClause());
        if (anchors.isEmpty()) {
            addClauseAnchors(anchors, contractText, node.getSourceClause());
        }
        if (anchors.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        List<int[]> sectionRanges = moneySectionRanges(contractText, anchors);
        Matcher matcher = moneyPattern().matcher(contractText);
        BigDecimal nearestAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int nearestDistance = Integer.MAX_VALUE;
        while (matcher.find()) {
            BigDecimal amount = moneyAmount(matcher);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            int center = (matcher.start() + matcher.end()) / 2;
            if (!sectionRanges.isEmpty()
                    && sectionRanges.stream().noneMatch(range -> center >= range[0] && center < range[1])) {
                continue;
            }
            int distance = anchors.stream().mapToInt(anchor -> Math.abs(anchor - center)).min().orElse(Integer.MAX_VALUE);
            if (distance < nearestDistance && distance <= 500) {
                nearestDistance = distance;
                nearestAmount = amount;
            }
        }
        return nearestAmount;
    }

    private List<int[]> moneySectionRanges(String text, List<Integer> anchors) {
        Matcher headingMatcher = Pattern.compile("(?:^|\\s)(?:第[零〇一二三四五六七八九十百两\\d]+条|[零〇一二三四五六七八九十百两\\d]+、)")
                .matcher(text);
        List<Integer> headings = new ArrayList<>();
        while (headingMatcher.find()) {
            headings.add(headingMatcher.start());
        }
        if (headings.isEmpty()) {
            return List.of();
        }
        List<int[]> ranges = new ArrayList<>();
        for (Integer anchor : anchors) {
            int start = -1;
            int end = text.length();
            for (Integer heading : headings) {
                if (heading <= anchor) {
                    start = heading;
                } else {
                    end = heading;
                    break;
                }
            }
            boolean duplicate = false;
            for (int[] range : ranges) {
                if (range[0] == start && range[1] == end) {
                    duplicate = true;
                    break;
                }
            }
            if (start >= 0 && !duplicate) {
                ranges.add(new int[]{start, end});
            }
        }
        return ranges;
    }

    private BigDecimal firstMoneyAmount(String text) {
        if (!StringUtils.hasText(text)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        Matcher matcher = moneyPattern().matcher(text);
        return matcher.find() ? moneyAmount(matcher) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private Pattern moneyPattern() {
        return Pattern.compile("(?i)(人民币|RMB|CNY|[￥¥])?\\s*([0-9][0-9,，]*(?:\\.\\d{1,2})?)\\s*(亿元|万元|元)?");
    }

    private BigDecimal moneyAmount(Matcher matcher) {
        String currency = matcher.group(1);
        String unit = matcher.group(3);
        if (!StringUtils.hasText(currency) && !StringUtils.hasText(unit)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        try {
            BigDecimal value = new BigDecimal(matcher.group(2).replace(",", "").replace("，", ""));
            BigDecimal multiplier = switch (Objects.toString(unit, "元")) {
                case "亿元" -> new BigDecimal("100000000");
                case "万元" -> new BigDecimal("10000");
                default -> BigDecimal.ONE;
            };
            return money(value.multiply(multiplier));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }

    private void addTextAnchors(List<Integer> anchors, String text, String token) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(token)) {
            return;
        }
        int fromIndex = 0;
        while (fromIndex < text.length()) {
            int index = text.indexOf(token.trim(), fromIndex);
            if (index < 0) {
                return;
            }
            anchors.add(index + token.trim().length() / 2);
            fromIndex = index + token.trim().length();
        }
    }

    private void addClauseAnchors(List<Integer> anchors, String text, String sourceClause) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(sourceClause)) {
            return;
        }
        Matcher clause = Pattern.compile("第([零〇一二三四五六七八九十百两\\d]+)条").matcher(sourceClause);
        if (!clause.find()) {
            return;
        }
        String ordinal = clause.group(1);
        addTextAnchors(anchors, text, "第" + ordinal + "条");
        addTextAnchors(anchors, text, ordinal + "、");
        addTextAnchors(anchors, text, ordinal + ".");
        Integer number = parseChineseInteger(ordinal.replace('两', '二').replace('〇', '零'));
        if (number != null) {
            String chinese = chineseNumber(number);
            addTextAnchors(anchors, text, chinese + "、");
            addTextAnchors(anchors, text, number + "、");
            addTextAnchors(anchors, text, number + ".");
        }
    }

    private String chineseNumber(int value) {
        String[] digits = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        if (value >= 0 && value < 10) {
            return digits[value];
        }
        if (value >= 10 && value < 20) {
            return "十" + (value == 10 ? "" : digits[value % 10]);
        }
        if (value < 100) {
            return digits[value / 10] + "十" + (value % 10 == 0 ? "" : digits[value % 10]);
        }
        return Integer.toString(value);
    }

    private String paymentConditionFromNode(FulfillmentPlan node) {
        String source = Objects.toString(node.getSourceClause(), "").trim();
        if (StringUtils.hasText(source)) {
            return clip(source, 300);
        }
        return switch (paymentConditionType(node)) {
            case "DELIVERABLE" -> "交付确认后付款";
            case "ACCEPTANCE" -> "验收确认后付款";
            case "INVOICE" -> "发票齐全后付款";
            default -> "无前置付款条件";
        };
    }

    private String paymentConditionType(FulfillmentPlan node) {
        String text = (Objects.toString(node.getNodeName(), "") + " " + Objects.toString(node.getSourceClause(), "")).toLowerCase();
        if ("INVOICE".equals(normalizePlanType(node.getPlanType())) || text.contains("发票")) {
            return "INVOICE";
        }
        if (text.contains("验收")) {
            return "ACCEPTANCE";
        }
        if (text.contains("交付")) {
            return "DELIVERABLE";
        }
        return "NONE";
    }

    private String prerequisiteFromPaymentNode(FulfillmentPlan node) {
        return switch (paymentConditionType(node)) {
            case "DELIVERABLE" -> "交付成果文件";
            case "ACCEPTANCE" -> "验收报告";
            case "INVOICE" -> "发票";
            default -> "";
        };
    }

    private FulfillmentDeliverable deliverable(Long contractId, String type, String name, String stage) {
        FulfillmentDeliverable item = new FulfillmentDeliverable();
        item.setContractId(contractId);
        item.setDeliverableType(type);
        item.setDeliverableName(name);
        item.setStageName(stage);
        item.setConfirmMethod("逐项勾选确认");
        item.setStatus(DELIVERABLE_PENDING_SUBMIT);
        item.setConfirmed(0);
        item.setSubmissionVersion(0);
        item.setRemark("");
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        item.setDeleted(0);
        return item;
    }

    private FulfillmentDeliverable deliverable(Long contractId, FulfillmentPlan plan, String type, String name, String stage) {
        FulfillmentDeliverable item = new FulfillmentDeliverable();
        item.setContractId(contractId);
        item.setPlanId(plan == null ? null : plan.getPlanId());
        item.setDeliverableType(type);
        item.setDeliverableName(name);
        item.setStageName(stage);
        item.setConfirmMethod("CHECKLIST");
        item.setStatus(DELIVERABLE_PENDING_SUBMIT);
        item.setConfirmed(0);
        item.setConfirmStatus(deliverableNeedsManualConfirm(plan) ? "PENDING_CONFIRM" : "CONFIRMED");
        item.setSourceClause(plan == null ? null : clip(plan.getSourceClause(), 500));
        item.setAiConfidence(plan == null ? null : plan.getAiConfidence());
        item.setAiExtracted(plan != null && Integer.valueOf(1).equals(plan.getAiExtracted()) ? 1 : 0);
        item.setFileId(null);
        item.setSubmissionVersion(0);
        item.setRemark("");
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        item.setDeleted(0);
        return item;
    }

    private boolean deliverableNeedsManualConfirm(FulfillmentPlan plan) {
        if (plan == null || !Integer.valueOf(1).equals(plan.getAiExtracted())) {
            return false;
        }
        BigDecimal confidence = plan.getAiConfidence();
        return confidence == null
                || confidence.compareTo(AI_CONFIRM_THRESHOLD) < 0
                || !StringUtils.hasText(plan.getSourceClause())
                || STATUS_PENDING_CONFIRM.equals(normalizeStatus(plan.getStatus()));
    }

    private PaymentPlan paymentPlan(Long contractId, String phaseName, BigDecimal percentage,
                                    BigDecimal contractAmount, LocalDate dueDate, String prerequisite) {
        PaymentPlan plan = new PaymentPlan();
        plan.setContractId(contractId);
        plan.setPhaseName(phaseName);
        plan.setPercentage(percentage);
        plan.setPlannedAmount(money(contractAmount.multiply(percentage).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP)));
        plan.setDueDate(dueDate);
        plan.setPayee("");
        plan.setPaymentCondition(StringUtils.hasText(prerequisite) ? prerequisite + "确认后付款" : "无前置付款条件");
        plan.setConditionType(StringUtils.hasText(prerequisite) ? "DELIVERABLE" : "NONE");
        plan.setConditionStatus("PENDING");
        plan.setPrerequisiteDelivery(prerequisite);
        plan.setPenaltyRate(DEFAULT_PENALTY_RATE);
        plan.setStatus("READY_TO_PAY");
        plan.setRemark("");
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        plan.setDeleted(0);
        syncPaymentPlanStatusFields(plan);
        return plan;
    }

    private List<FulfillmentPlan> fallbackPlans(Long contractId, ContractMain contract, String reason) {
        LocalDate baseDate = fallbackDueDate(contract);
        return List.of(
                autoPlan(contractId, "履约材料准备", "PREPARE", baseDate.minusDays(30), reason + "：到期日前30天"),
                autoPlan(contractId, "履约进度确认", "CHECK", baseDate.minusDays(7), reason + "：到期日前7天"),
                autoPlan(contractId, "最终履约验收", "ACCEPTANCE", baseDate, reason + "：合同到期日")
        );
    }

    private FulfillmentPlan aiPlan(Long contractId,
                                   ContractMain contract,
                                   AiDraftService.FulfillmentNode node,
                                   List<AiDraftService.FulfillmentNode> allNodes,
                                   LocalDate archiveDate) {
        LocalDate dueDate = resolvePlannedDate(node, allNodes, contract, archiveDate);
        BigDecimal confidence = confidence(node.confidence());
        boolean pendingConfirm = dueDate == null
                || confidence == null
                || confidence.compareTo(AI_CONFIRM_THRESHOLD) < 0;
        FulfillmentPlan plan = new FulfillmentPlan();
        plan.setContractId(contractId);
        plan.setNodeName(clip(StringUtils.hasText(node.nodeName()) ? node.nodeName().trim() : "AI识别履约节点", 120));
        plan.setPlanType(normalizePlanType(node.nodeType()));
        plan.setDueDate(dueDate);
        plan.setStatus(pendingConfirm ? STATUS_PENDING_CONFIRM : STATUS_NOT_STARTED);
        plan.setProgress(0);
        plan.setOwnerName(resolveOwnerName(node.responsibleParty()));
        plan.setSourceType("AI");
        plan.setSourceClause(clip(node.sourceClause(), 500));
        plan.setAiConfidence(confidence);
        plan.setAiExtracted(1);
        plan.setConfirmStatus(pendingConfirm ? "PENDING_CONFIRM" : "CONFIRMED");
        plan.setOverdueDays(0);
        plan.setDelayStatus("NONE");
        plan.setExtractedRule(buildAiExtractedRule(node));
        plan.setRemark(pendingConfirm ? "待人工确认：AI置信度低于0.80或日期不明确" : "AI从合同正文抽取，请人工复核后使用");
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        plan.setDeleted(0);
        return plan;
    }

    private LocalDate resolvePlannedDate(AiDraftService.FulfillmentNode node,
                                         List<AiDraftService.FulfillmentNode> allNodes,
                                         ContractMain contract,
                                         LocalDate archiveDate) {
        if (node.plannedDate() != null) {
            return node.plannedDate();
        }
        String expression = Objects.toString(node.sourceClause(), "") + " " + Objects.toString(node.nodeName(), "");
        LocalDate signBase = firstNonNull(contract.getSignDate(),
                contract.getCreatedAt() != null ? contract.getCreatedAt().toLocalDate() : null);
        LocalDate resolved = parseRelativeDate(expression, List.of("签署", "签订", "生效", "合同生效", "合同签署"), signBase);
        if (resolved != null) {
            return resolved;
        }
        resolved = parseRelativeDate(expression, List.of("归档", "归档完成"), archiveDate);
        if (resolved != null) {
            return resolved;
        }
        resolved = parseDependentRelativeDate(expression, allNodes, contract, archiveDate);
        return resolved;
    }

    private LocalDate parseDependentRelativeDate(String expression,
                                                 List<AiDraftService.FulfillmentNode> allNodes,
                                                 ContractMain contract,
                                                 LocalDate archiveDate) {
        Map<String, List<String>> dependencyKeywords = Map.of(
                "ACCEPTANCE", List.of("验收", "验收通过"),
                "DELIVERY", List.of("交付", "交付完成"),
                "INVOICE", List.of("发票", "开票", "收到发票"),
                "PAYMENT", List.of("付款", "支付")
        );
        for (Map.Entry<String, List<String>> entry : dependencyKeywords.entrySet()) {
            if (entry.getValue().stream().noneMatch(expression::contains)) {
                continue;
            }
            LocalDate base = dependentNodeDate(entry.getKey(), allNodes);
            if (base == null) {
                continue;
            }
            LocalDate resolved = parseRelativeDate(expression, entry.getValue(), base);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private LocalDate dependentNodeDate(String planType, List<AiDraftService.FulfillmentNode> allNodes) {
        for (AiDraftService.FulfillmentNode candidate : allNodes) {
            if (candidate.plannedDate() == null) {
                continue;
            }
            if (planType.equals(normalizePlanType(candidate.nodeType()))) {
                return candidate.plannedDate();
            }
        }
        return null;
    }

    private LocalDate parseRelativeDate(String expression, List<String> anchors, LocalDate baseDate) {
        if (!StringUtils.hasText(expression) || baseDate == null) {
            return null;
        }
        for (String anchor : anchors) {
            Pattern pattern = Pattern.compile(anchor + "[^，。；;]{0,20}?后\\s*([0-9一二三四五六七八九十百]+)\\s*个?\\s*(工作日|自然日|日|天)?");
            Matcher matcher = pattern.matcher(expression);
            if (matcher.find()) {
                Integer days = parseChineseInteger(matcher.group(1));
                if (days == null) {
                    continue;
                }
                boolean workday = "工作日".equals(matcher.group(2));
                return workday ? addWorkdays(baseDate, days) : baseDate.plusDays(days);
            }
        }
        return null;
    }

    private LocalDate addWorkdays(LocalDate baseDate, int days) {
        LocalDate date = baseDate;
        int added = 0;
        while (added < days) {
            date = date.plusDays(1);
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return date;
    }

    private Integer parseChineseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        if (text.matches("\\d+")) {
            return Integer.parseInt(text);
        }
        Map<Character, Integer> digits = Map.of(
                '零', 0, '一', 1, '二', 2, '三', 3, '四', 4,
                '五', 5, '六', 6, '七', 7, '八', 8, '九', 9
        );
        int total = 0;
        int current = 0;
        for (char ch : text.toCharArray()) {
            if (digits.containsKey(ch)) {
                current = digits.get(ch);
            } else if (ch == '十') {
                total += current == 0 ? 10 : current * 10;
                current = 0;
            } else if (ch == '百') {
                total += current == 0 ? 100 : current * 100;
                current = 0;
            } else {
                return null;
            }
        }
        return total + current;
    }

    private LocalDate firstNonNull(LocalDate first, LocalDate second) {
        return first != null ? first : second;
    }

    private BigDecimal confidence(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return null;
        }
        double normalized = Math.max(0D, Math.min(1D, value));
        return BigDecimal.valueOf(normalized).setScale(4, RoundingMode.HALF_UP);
    }

    private String resolveArchivedContractText(Long contractId) {
        ContractVersion version = contractService.listArchiveRecords(contractId).stream()
                .map(ArchiveRecord::getVersionId)
                .filter(Objects::nonNull)
                .map(contractVersionService::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (version == null) {
            version = contractVersionService.latest(contractId);
        }
        if (version == null) {
            return "";
        }
        String content = contractVersionService.toVo(version).content();
        return Jsoup.parse(Objects.toString(content, "")).text().trim();
    }

    private LocalDate resolveArchiveDate(Long contractId) {
        return contractService.listArchiveRecords(contractId).stream()
                .map(ArchiveRecord::getArchiveTime)
                .filter(Objects::nonNull)
                .findFirst()
                .map(LocalDateTime::toLocalDate)
                .orElse(LocalDate.now());
    }

    private String sanitizeContractText(String text) {
        String sanitized = Objects.toString(text, "");
        sanitized = sanitized.replaceAll("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", "[EMAIL]");
        sanitized = sanitized.replaceAll("\\b\\d{17}[0-9Xx]\\b", "[ID_CARD]");
        sanitized = sanitized.replaceAll("\\b(?=.*[A-Z])[0-9A-Z]{18}\\b", "[USCC]");
        sanitized = sanitized.replaceAll("1[3-9]\\d{9}", "[PHONE]");
        sanitized = sanitized.replaceAll("\\b\\d{12,19}\\b", "[BANK_CARD]");
        sanitized = sanitized.replaceAll("(人民币|¥|￥)\\s*[0-9,，.]+\\s*(元|万元|亿元)?", "[AMOUNT]");
        return sanitized;
    }

    private AiTaskRecord startAiTask(Long contractId, String sanitizedText) {
        if (aiTaskRecordMapper == null) {
            return null;
        }
        AiTaskRecord record = new AiTaskRecord();
        record.setContractId(contractId);
        record.setTaskType("FULFILLMENT_NODE_EXTRACT");
        record.setModelName(aiDraftService != null ? aiDraftService.modelName() : "");
        record.setPromptHash(sha256(sanitizedText));
        record.setTokenUsage(0);
        record.setStatus("RUNNING");
        record.setInputSummary(inputSummary(sanitizedText));
        record.setCreatedBy(StringUtils.hasText(SecurityContext.username()) ? SecurityContext.username() : SecurityContext.roleCode());
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        aiTaskRecordMapper.insert(record);
        return record;
    }

    private void finishAiTask(AiTaskRecord record, String status, String outputSummary, String errorReason, long startedAt) {
        if (record == null || aiTaskRecordMapper == null) {
            return;
        }
        record.setStatus(status);
        record.setOutputSummary(clip(outputSummary, 500));
        record.setErrorReason(clip(errorReason, 500));
        record.setDurationMs(Math.max(0L, System.currentTimeMillis() - startedAt));
        record.setUpdatedAt(LocalDateTime.now());
        aiTaskRecordMapper.updateById(record);
    }

    private String inputSummary(String sanitizedText) {
        String compact = Objects.toString(sanitizedText, "").replaceAll("\\s+", " ").trim();
        return clip("脱敏合同正文，长度=" + compact.length() + "，摘要=" + compact, 500);
    }

    private String aiOutputSummary(List<FulfillmentPlan> plans) {
        long pending = plans.stream().filter(plan -> "PENDING_CONFIRM".equals(plan.getStatus())).count();
        long active = plans.stream().filter(plan -> STATUS_NOT_STARTED.equals(normalizeStatus(plan.getStatus()))).count();
        String names = plans.stream().map(FulfillmentPlan::getNodeName).filter(StringUtils::hasText)
                .limit(8)
                .collect(Collectors.joining("、"));
        return "生成履约节点" + plans.size() + "个，NOT_STARTED=" + active + "，PENDING_CONFIRM=" + pending + "；节点：" + names;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(Objects.toString(value, "").getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            return "";
        }
    }

    private String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null || current.getMessage() == null ? "未知错误" : current.getMessage();
    }

    private LocalDate fallbackDueDate(ContractMain contract) {
        return contract.getDueDate() != null ? contract.getDueDate() : LocalDate.now().plusDays(45);
    }

    private String normalizePlanType(String nodeType) {
        if (!StringUtils.hasText(nodeType)) {
            return "OTHER";
        }
        String value = nodeType.trim().toUpperCase();
        if (value.contains("PAYMENT") || value.contains("付款") || value.contains("回款")) return "PAYMENT";
        if (value.contains("DELIVERY") || value.contains("交付")) return "DELIVERY";
        if (value.contains("ACCEPTANCE") || value.contains("验收")) return "ACCEPTANCE";
        if (value.contains("WARRANTY") || value.contains("质保")) return "WARRANTY";
        if (value.contains("RENEWAL") || value.contains("续签")) return "RENEWAL";
        if (value.contains("TERMINATION") || value.contains("终止")) return "TERMINATION";
        if (value.contains("INVOICE") || value.contains("发票")) return "INVOICE";
        if (value.contains("CONFIDENTIALITY") || value.contains("保密")) return "CONFIDENTIALITY";
        return "OTHER";
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return STATUS_NOT_STARTED;
        }
        String value = status.trim().toUpperCase();
        return switch (value) {
            case "TODO", "ACTIVE", "NOT_STARTED" -> STATUS_NOT_STARTED;
            case "PROCESSING", "IN_PROGRESS" -> STATUS_IN_PROGRESS;
            case "DONE", "FINISHED", "COMPLETED" -> STATUS_COMPLETED;
            case "OVERDUE" -> STATUS_OVERDUE;
            case "HANDLED", "CLOSE", "CLOSED" -> STATUS_CLOSED;
            case "PENDING_CONFIRM" -> STATUS_PENDING_CONFIRM;
            default -> value;
        };
    }

    private List<String> statusFilterValues(String status) {
        String normalized = normalizeStatus(status);
        return switch (normalized) {
            case STATUS_NOT_STARTED -> List.of(STATUS_NOT_STARTED, "TODO", "ACTIVE");
            case STATUS_IN_PROGRESS -> List.of(STATUS_IN_PROGRESS, "PROCESSING");
            case STATUS_COMPLETED -> List.of(STATUS_COMPLETED, "DONE", "FINISHED");
            case STATUS_CLOSED -> List.of(STATUS_CLOSED, "HANDLED", "CLOSE");
            default -> List.of(normalized);
        };
    }

    private String resolveOwnerName(String responsibleParty) {
        if (StringUtils.hasText(responsibleParty) && !"未知".equals(responsibleParty.trim())) {
            return clip(responsibleParty.trim(), 80);
        }
        return StringUtils.hasText(SecurityContext.username()) ? SecurityContext.username() : "合同负责人";
    }

    private String buildAiExtractedRule(AiDraftService.FulfillmentNode node) {
        StringBuilder sb = new StringBuilder("AI从合同正文抽取");
        if (StringUtils.hasText(node.sourceClause())) {
            sb.append("；来源：").append(node.sourceClause().trim());
        }
        if (node.confidence() != null) {
            sb.append("；置信度：").append(BigDecimal.valueOf(node.confidence()).setScale(2, RoundingMode.HALF_UP));
        }
        return clip(sb.toString(), 200);
    }

    private FulfillmentPlan autoPlan(Long contractId, String name, String type, LocalDate dueDate, String rule) {
        FulfillmentPlan plan = new FulfillmentPlan();
        plan.setContractId(contractId);
        plan.setNodeName(name);
        plan.setPlanType(type);
        plan.setDueDate(dueDate);
        plan.setStatus(STATUS_NOT_STARTED);
        plan.setProgress(0);
        plan.setOwnerName(StringUtils.hasText(SecurityContext.username()) ? SecurityContext.username() : "合同负责人");
        plan.setSourceType("AUTO");
        plan.setExtractedRule(rule);
        plan.setSourceClause(null);
        plan.setAiConfidence(null);
        plan.setAiExtracted(0);
        plan.setConfirmStatus("CONFIRMED");
        plan.setOverdueDays(0);
        plan.setDelayStatus("NONE");
        plan.setRemark("");
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        plan.setDeleted(0);
        return plan;
    }

    private void applyPlanRequest(FulfillmentPlan plan, FulfillmentPlanRequest request) {
        applyPlanRequest(plan, request, true);
    }

    private void applyPlanRequest(FulfillmentPlan plan, FulfillmentPlanRequest request, boolean applyDueDate) {
        if (request.contractId() != null) {
            contractService.assertCanAccess(request.contractId());
            plan.setContractId(request.contractId());
        }
        if (StringUtils.hasText(request.nodeName())) {
            plan.setNodeName(request.nodeName().trim());
        }
        plan.setPlanType(StringUtils.hasText(request.planType()) ? request.planType().trim() : "OTHER");
        if (applyDueDate) {
            plan.setDueDate(request.dueDate());
        }
        int progress = request.progress() != null ? Math.max(0, Math.min(100, request.progress())) : 0;
        plan.setProgress(progress);
        String status = normalizeStatus(StringUtils.hasText(request.status())
                ? request.status().trim()
                : (progress >= 100 ? STATUS_COMPLETED : STATUS_NOT_STARTED));
        if (progress >= 100) {
            status = STATUS_COMPLETED;
        } else if (STATUS_COMPLETED.equals(status)) {
            plan.setProgress(100);
        }
        if (plan.getDueDate() == null && !CLOSED_STATUS.contains(status)) {
            status = STATUS_PENDING_CONFIRM;
        }
        plan.setStatus(status);
        LocalDate requestedActualCompletedDate = request.actualCompletedDate();
        validateActualCompletedDate(requestedActualCompletedDate);
        if (STATUS_COMPLETED.equals(status)) {
            LocalDate actualCompletedDate = requestedActualCompletedDate != null
                    ? requestedActualCompletedDate
                    : firstNonNull(plan.getActualCompletedDate(), LocalDate.now());
            validateActualCompletedDate(actualCompletedDate);
            plan.setActualCompletedDate(actualCompletedDate);
        } else {
            plan.setActualCompletedDate(null);
        }
        if (STATUS_PENDING_CONFIRM.equals(status)) {
            plan.setConfirmStatus(STATUS_PENDING_CONFIRM);
        } else {
            plan.setConfirmStatus("CONFIRMED");
        }
        plan.setOwnerName(StringUtils.hasText(request.ownerName()) ? request.ownerName().trim() : SecurityContext.username());
        plan.setRemark(StringUtils.hasText(request.remark()) ? request.remark().trim() : "");
    }

    private void validateActualCompletedDate(LocalDate actualCompletedDate) {
        if (actualCompletedDate != null && actualCompletedDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("\u5b9e\u9645\u5b8c\u6210\u65e5\u671f\u4e0d\u80fd\u665a\u4e8e\u4eca\u5929");
        }
    }

    private void applyDeliverableRequest(FulfillmentDeliverable item,
                                         DeliverableRequest request,
                                         boolean creating) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        Long contractId = request.contractId();
        if (request.planId() != null) {
            FulfillmentPlan plan = assertPlanAccess(request.planId());
            item.setPlanId(plan.getPlanId());
            contractId = plan.getContractId();
        } else if (creating) {
            item.setPlanId(null);
        }
        if (contractId != null) {
            contractService.assertCanAccess(contractId);
            item.setContractId(contractId);
        }
        if (item.getContractId() == null) {
            throw new IllegalArgumentException("contractId is required");
        }
        if (StringUtils.hasText(request.deliverableType())) {
            item.setDeliverableType(request.deliverableType().trim());
        } else if (creating || !StringUtils.hasText(item.getDeliverableType())) {
            item.setDeliverableType("OTHER");
        }
        if (StringUtils.hasText(request.deliverableName())) {
            item.setDeliverableName(clip(request.deliverableName().trim(), 120));
        } else if (creating) {
            throw new IllegalArgumentException("deliverableName is required");
        }
        if (StringUtils.hasText(request.stageName())) {
            item.setStageName(clip(request.stageName().trim(), 80));
        } else if (creating || !StringUtils.hasText(item.getStageName())) {
            item.setStageName("");
        }
        if (StringUtils.hasText(request.confirmStatus())) {
            item.setConfirmStatus(normalizeDeliverableConfirmStatus(request.confirmStatus()));
        } else if (creating || !StringUtils.hasText(item.getConfirmStatus())) {
            item.setConfirmStatus("CONFIRMED");
        }
        if (creating || !StringUtils.hasText(item.getConfirmMethod())) {
            item.setConfirmMethod("CHECKLIST");
        }
        item.setRemark(StringUtils.hasText(request.remark()) ? clip(request.remark().trim(), 500) : "");
    }

    private String normalizeDeliverableConfirmStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "CONFIRMED";
        }
        String normalized = status.trim().toUpperCase();
        return STATUS_PENDING_CONFIRM.equals(normalized) ? STATUS_PENDING_CONFIRM : "CONFIRMED";
    }

    private boolean isDelayRequest(FulfillmentPlan plan, FulfillmentPlanRequest request) {
        if (plan == null || request == null || plan.getDueDate() == null || request.dueDate() == null) {
            return false;
        }
        if (CLOSED_STATUS.contains(normalizeStatus(plan.getStatus()))) {
            return false;
        }
        return request.dueDate().isAfter(plan.getDueDate());
    }

    private void requestDelay(FulfillmentPlan plan, LocalDate requestedDueDate, String delayReason) {
        validateDelayRequestedDate(plan, requestedDueDate);
        String reason = delayReason;
        if (!StringUtils.hasText(reason)) {
            throw new IllegalArgumentException("延期调整必须填写延期原因，并由部门主管确认后生效");
        }
        plan.setDelayStatus("PENDING");
        plan.setDelayRequestedDueDate(requestedDueDate);
        plan.setDelayReason(clip(reason, 500));
        plan.setDelayRequestedBy(StringUtils.hasText(SecurityContext.username()) ? SecurityContext.username() : SecurityContext.roleCode());
        plan.setDelayRequestedAt(LocalDateTime.now());
        plan.setDelayConfirmedBy(null);
        plan.setDelayConfirmedAt(null);
        plan.setDelayRejectedBy(null);
        plan.setDelayRejectedAt(null);
        plan.setDelayRejectReason(null);
    }

    private void validateDelayRequestedDate(FulfillmentPlan plan, LocalDate requestedDueDate) {
        if (requestedDueDate == null) {
            throw new IllegalArgumentException("请填写新的计划日期");
        }
        if (!requestedDueDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("新的计划日期必须晚于今天，审批通过后才会重新进入预警调度");
        }
        if (plan != null && plan.getDueDate() != null && !requestedDueDate.isAfter(plan.getDueDate())) {
            throw new IllegalArgumentException("新的计划日期必须晚于原计划日期");
        }
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return clip(value.trim(), 500);
    }

    private void assertCompletionVoucherReady(FulfillmentPlan plan) {
        List<FulfillmentVoucher> vouchers = voucherMapper.selectList(new LambdaQueryWrapper<FulfillmentVoucher>()
                .eq(FulfillmentVoucher::getPlanId, plan.getPlanId())
                .orderByDesc(FulfillmentVoucher::getUploadedAt));
        boolean ready = vouchers.stream()
                .filter(voucher -> !"REJECTED".equals(voucher.getReviewStatus()))
                .anyMatch(voucher -> {
                    String type = Objects.toString(voucher.getVoucherType(), "").toUpperCase();
                    if ("PAYMENT".equals(plan.getPlanType())) {
                        return "PAYMENT".equals(type) && "APPROVED".equals(voucher.getReviewStatus());
                    }
                    return "COMPLETION".equals(type) || "PROGRESS".equals(type) || "EXCEPTION".equals(type);
                });
        if (!ready) {
            throw new IllegalStateException("请先上传有效履约凭证；付款节点需上传付款凭证并完成复核");
        }
    }

    private FulfillmentDelayApproval createDelayApproval(FulfillmentPlan plan, LocalDate requestedDueDate, String delayReason) {
        if (delayApprovalMapper == null) {
            return null;
        }
        cancelPendingDelayApprovals(plan.getPlanId(), "REPLACED");
        LocalDateTime now = LocalDateTime.now();
        FulfillmentDelayApproval approval = new FulfillmentDelayApproval();
        approval.setPlanId(plan.getPlanId());
        approval.setContractId(plan.getContractId());
        approval.setOriginalDueDate(plan.getDueDate());
        approval.setRequestedDueDate(requestedDueDate);
        approval.setDelayReason(clip(delayReason, 500));
        approval.setStatus("PENDING");
        approval.setRequesterId(SecurityContext.userId());
        approval.setRequesterName(StringUtils.hasText(SecurityContext.username()) ? SecurityContext.username() : "system");
        approval.setRequestedAt(now);
        approval.setNoticeStatus("PUSHED");
        approval.setCreatedAt(now);
        approval.setUpdatedAt(now);
        approval.setDeleted(0);
        delayApprovalMapper.insert(approval);
        return delayApprovalMapper.selectById(approval.getApprovalId());
    }

    private void cancelPendingDelayApprovals(Long planId, String status) {
        if (delayApprovalMapper == null) {
            return;
        }
        List<FulfillmentDelayApproval> pending = delayApprovalMapper.selectList(new LambdaQueryWrapper<FulfillmentDelayApproval>()
                .eq(FulfillmentDelayApproval::getPlanId, planId)
                .eq(FulfillmentDelayApproval::getStatus, "PENDING"));
        for (FulfillmentDelayApproval approval : pending) {
            approval.setStatus(status);
            approval.setUpdatedAt(LocalDateTime.now());
            delayApprovalMapper.updateById(approval);
        }
    }

    private FulfillmentDelayApproval latestPendingDelayApproval(Long planId) {
        if (delayApprovalMapper == null) {
            return null;
        }
        return delayApprovalMapper.selectOne(new LambdaQueryWrapper<FulfillmentDelayApproval>()
                .eq(FulfillmentDelayApproval::getPlanId, planId)
                .eq(FulfillmentDelayApproval::getStatus, "PENDING")
                .orderByDesc(FulfillmentDelayApproval::getRequestedAt)
                .last("LIMIT 1"));
    }

    private boolean canConfirmDelay() {
        String role = SecurityContext.roleCode();
        return "DEPT_LEADER".equals(role) || "ADMIN".equals(role);
    }

    private void applyPaymentPlanRequest(PaymentPlan plan, PaymentPlanRequest request) {
        if (request.contractId() != null) {
            contractService.assertCanAccess(request.contractId());
            plan.setContractId(request.contractId());
        }
        plan.setFulfillmentPlanId(request.fulfillmentPlanId());
        plan.setPhaseName(StringUtils.hasText(request.phaseName()) ? request.phaseName().trim() : "付款节点");
        plan.setPercentage(request.percentage() != null ? request.percentage() : BigDecimal.ZERO);
        plan.setPlannedAmount(money(request.plannedAmount()));
        plan.setDueDate(request.dueDate() != null ? request.dueDate() : LocalDate.now());
        plan.setPayee(StringUtils.hasText(request.payee()) ? request.payee().trim() : "");
        plan.setPaymentCondition(StringUtils.hasText(request.paymentCondition()) ? request.paymentCondition().trim() : "");
        plan.setConditionType(normalizeConditionType(request.conditionType()));
        plan.setPrerequisiteDelivery(StringUtils.hasText(request.prerequisiteDelivery()) ? request.prerequisiteDelivery().trim() : "");
        plan.setPenaltyRate(request.penaltyRate() != null ? request.penaltyRate() : DEFAULT_PENALTY_RATE);
        if (StringUtils.hasText(request.status())) {
            plan.setStatus(request.status().trim());
        } else if (!StringUtils.hasText(plan.getStatus())) {
            plan.setStatus("READY_TO_PAY");
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
        if (!confirmed) {
            applyAcceptance(item, false);
        }
    }

    private void applyAcceptance(FulfillmentDeliverable item, boolean acceptancePassed) {
        if (acceptancePassed && !Integer.valueOf(1).equals(item.getConfirmed())) {
            throw new IllegalArgumentException("交付物确认后才能进行验收确认");
        }
        item.setAcceptancePassed(acceptancePassed ? 1 : 0);
        item.setAcceptedBy(acceptancePassed ? SecurityContext.username() : null);
        item.setAcceptedAt(acceptancePassed ? LocalDateTime.now() : null);
    }

    private void assertDeliverableEditable(FulfillmentDeliverable item) {
        String status = normalizeDeliverableStatus(item.getStatus());
        if (!List.of(DELIVERABLE_PENDING_SUBMIT, DELIVERABLE_NEED_SUPPLEMENT, DELIVERABLE_REJECTED).contains(status)) {
            throw new IllegalStateException("当前交付物已提交审核，不能直接编辑、替换文件或删除");
        }
    }

    private void assertDeliverableReviewAccess(FulfillmentDeliverable item) {
        ContractMain contract = contractService.findContract(item.getContractId());
        String role = SecurityContext.roleCode();
        if ("ADMIN".equals(role)) {
            return;
        }
        if ("DEPT_LEADER".equals(role)
                && SecurityContext.deptId() != null
                && SecurityContext.deptId().equals(contract.getDeptId())) {
            return;
        }
        throw new SecurityException("仅部门主管或管理员可审核和验收确认交付物");
    }

    private void transitionDeliverableToSubmitted(FulfillmentDeliverable item) {
        String status = normalizeDeliverableStatus(item.getStatus());
        if (!List.of(DELIVERABLE_PENDING_SUBMIT, DELIVERABLE_NEED_SUPPLEMENT, DELIVERABLE_REJECTED).contains(status)) {
            throw new IllegalStateException("当前状态不能提交交付物");
        }
        if (item.getFileId() == null) {
            throw new IllegalStateException("提交前必须上传交付物文件");
        }
        LocalDateTime now = LocalDateTime.now();
        item.setStatus(DELIVERABLE_SUBMITTED);
        item.setSubmittedBy(currentUserLabel());
        item.setSubmittedAt(now);
        item.setSubmissionVersion((item.getSubmissionVersion() == null ? 0 : item.getSubmissionVersion()) + 1);
        item.setReviewerName(null);
        item.setReviewedAt(null);
        item.setReviewComment(null);
        applyConfirm(item, false);
    }

    private void transitionDeliverableReview(FulfillmentDeliverable item, String targetStatus, String reviewComment) {
        requireDeliverableStatus(item, DELIVERABLE_SUBMITTED);
        item.setStatus(targetStatus);
        item.setReviewerName(currentUserLabel());
        item.setReviewedAt(LocalDateTime.now());
        item.setReviewComment(clip(reviewComment, 500));
        applyConfirm(item, false);
    }

    private void transitionDeliverableAccepted(FulfillmentDeliverable item, String reviewComment) {
        requireDeliverableStatus(item, DELIVERABLE_SUBMITTED);
        if (item.getFileId() == null) {
            throw new IllegalStateException("交付物文件缺失，不能确认");
        }
        item.setStatus(DELIVERABLE_ACCEPTED);
        item.setReviewerName(currentUserLabel());
        item.setReviewedAt(LocalDateTime.now());
        item.setReviewComment(clip(reviewComment, 500));
        item.setConfirmStatus("CONFIRMED");
        applyConfirm(item, true);
    }

    private void transitionDeliverableAcceptancePassed(FulfillmentDeliverable item, String reviewComment) {
        requireDeliverableStatus(item, DELIVERABLE_ACCEPTED);
        item.setStatus(DELIVERABLE_ACCEPTANCE_PASSED);
        item.setReviewerName(currentUserLabel());
        item.setReviewedAt(LocalDateTime.now());
        if (StringUtils.hasText(reviewComment)) {
            item.setReviewComment(clip(reviewComment, 500));
        }
        applyAcceptance(item, true);
    }

    private void requireDeliverableStatus(FulfillmentDeliverable item, String requiredStatus) {
        String status = normalizeDeliverableStatus(item.getStatus());
        if (!requiredStatus.equals(status)) {
            throw new IllegalStateException("当前交付物状态不允许该操作");
        }
    }

    private String normalizeDeliverableStatus(String status) {
        if (!StringUtils.hasText(status) || "PENDING".equalsIgnoreCase(status)) {
            return DELIVERABLE_PENDING_SUBMIT;
        }
        String value = status.trim().toUpperCase();
        return switch (value) {
            case "CONFIRMED" -> DELIVERABLE_ACCEPTED;
            case DELIVERABLE_PENDING_SUBMIT, DELIVERABLE_SUBMITTED, DELIVERABLE_NEED_SUPPLEMENT,
                    DELIVERABLE_REJECTED, DELIVERABLE_ACCEPTED, DELIVERABLE_ACCEPTANCE_PASSED -> value;
            default -> DELIVERABLE_PENDING_SUBMIT;
        };
    }

    private void syncLinkedPlanFromDeliverable(FulfillmentDeliverable item, String clientIp) {
        if (item.getPlanId() == null) {
            return;
        }
        FulfillmentPlan plan = planMapper.selectById(item.getPlanId());
        if (plan == null || STATUS_OVERDUE.equals(normalizeStatus(plan.getStatus()))
                || CLOSED_STATUS.contains(normalizeStatus(plan.getStatus()))) {
            return;
        }
        boolean acceptanceNode = "ACCEPTANCE".equals(normalizePlanType(plan.getPlanType()));
        boolean completed = acceptanceNode
                ? DELIVERABLE_ACCEPTANCE_PASSED.equals(normalizeDeliverableStatus(item.getStatus()))
                : List.of(DELIVERABLE_ACCEPTED, DELIVERABLE_ACCEPTANCE_PASSED)
                        .contains(normalizeDeliverableStatus(item.getStatus()));
        if (!completed) {
            return;
        }
        String before = planSnapshot(plan);
        String beforeStatus = plan.getStatus();
        plan.setStatus(STATUS_COMPLETED);
        plan.setProgress(100);
        plan.setActualCompletedDate(LocalDate.now());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        writeProgressLog("交付物状态回写", plan.getPlanId(), plan.getContractId(), beforeStatus, plan.getStatus(),
                before, planSnapshot(plan), item.getReviewComment(), clientIp);
    }

    private void syncOverdueStatus() {
        List<FulfillmentPlan> overduePlans = planMapper.selectList(new LambdaQueryWrapper<FulfillmentPlan>()
                .lt(FulfillmentPlan::getDueDate, LocalDate.now())
                .notIn(FulfillmentPlan::getStatus, CLOSED_STATUS)
                .ne(FulfillmentPlan::getStatus, STATUS_PENDING_CONFIRM));
        for (FulfillmentPlan plan : overduePlans) {
            int overdueDays = (int) Math.max(0, ChronoUnit.DAYS.between(plan.getDueDate(), LocalDate.now()));
            String beforeStatus = plan.getStatus();
            String before = planSnapshot(plan);
            plan.setStatus(STATUS_OVERDUE);
            plan.setOverdueDays(overdueDays);
            if (plan.getLastOverdueAt() == null) {
                plan.setLastOverdueAt(LocalDateTime.now());
            }
            plan.setUpdatedAt(LocalDateTime.now());
            planMapper.updateById(plan);
            upsertOpenOverdueHistory(plan, overdueDays);
            if (!STATUS_OVERDUE.equals(normalizeStatus(beforeStatus))) {
                writeProgressLog("标记逾期", plan.getPlanId(), plan.getContractId(), beforeStatus, STATUS_OVERDUE,
                        before, planSnapshot(plan), "system overdue scan", "127.0.0.1");
            }
        }
    }

    private void upsertOpenOverdueHistory(FulfillmentPlan plan, int overdueDays) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM fulfillment_overdue_history
                WHERE plan_id = ?
                  AND status = 'OPEN'
                  AND is_deleted = 0
                """, Long.class, plan.getPlanId());
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE fulfillment_overdue_history
                    SET node_name = ?,
                        due_date = ?,
                        overdue_days = ?,
                        updated_at = ?
                    WHERE plan_id = ?
                      AND status = 'OPEN'
                      AND is_deleted = 0
                    """,
                    clip(plan.getNodeName(), 120),
                    plan.getDueDate(),
                    overdueDays,
                    LocalDateTime.now(),
                    plan.getPlanId());
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO fulfillment_overdue_history
                (plan_id, contract_id, node_name, due_date, overdue_days, status, started_at, created_at, updated_at, is_deleted)
                VALUES (?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, 0)
                """,
                plan.getPlanId(),
                plan.getContractId(),
                clip(plan.getNodeName(), 120),
                plan.getDueDate(),
                overdueDays,
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now());
    }

    private void closeOpenOverdueHistory(Long planId) {
        resolveOpenOverdueHistory(planId, "RESOLVED", null, null, null);
    }

    private void resolveOpenOverdueHistory(Long planId,
                                           String resolutionType,
                                           String disposalRemark,
                                           LocalDate actualCompletedDate,
                                           Long delayApprovalId) {
        jdbcTemplate.update("""
                UPDATE fulfillment_overdue_history
                SET status = 'RESOLVED',
                    resolved_at = ?,
                    resolution_type = ?,
                    disposal_remark = ?,
                    actual_completed_date = ?,
                    delay_approval_id = ?,
                    resolved_by = ?,
                    updated_at = ?
                WHERE plan_id = ?
                  AND status = 'OPEN'
                  AND is_deleted = 0
                """,
                LocalDateTime.now(),
                resolutionType,
                clip(disposalRemark, 500),
                actualCompletedDate,
                delayApprovalId,
                currentUserLabel(),
                LocalDateTime.now(),
                planId);
    }

    private void attachDelayApprovalToOpenOverdueHistory(FulfillmentPlan plan, Long approvalId, String disposalRemark) {
        int overdueDays = plan.getDueDate() == null ? 0 : (int) Math.max(0, ChronoUnit.DAYS.between(plan.getDueDate(), LocalDate.now()));
        upsertOpenOverdueHistory(plan, overdueDays);
        jdbcTemplate.update("""
                UPDATE fulfillment_overdue_history
                SET delay_approval_id = ?,
                    disposal_remark = ?,
                    updated_at = ?
                WHERE plan_id = ?
                  AND status = 'OPEN'
                  AND is_deleted = 0
                """, approvalId, clip(disposalRemark, 500), LocalDateTime.now(), plan.getPlanId());
    }

    private void notifyDelayApproval(FulfillmentDelayApproval approval, FulfillmentPlan plan) {
        ContractMain contract = contractMapper.selectById(plan.getContractId());
        String receiver = clip(String.join("；", roleReceivers("DEPT_LEADER",
                contract != null ? contract.getDeptId() : null, "部门主管")), 500);
        String content = clip("延期审批待处理：合同[" + (contract != null ? contract.getTitle() : plan.getContractId())
                + "]，节点[" + plan.getNodeName() + "]，原计划日期 "
                + Objects.toString(approval.getOriginalDueDate(), "-")
                + "，申请延期至 " + Objects.toString(approval.getRequestedDueDate(), "-")
                + "，原因：" + Objects.toString(approval.getDelayReason(), ""), 500);
        insertReminder(plan, "DELAY_APPROVAL", receiver, content);
    }

    private void notifyDelayDecision(FulfillmentDelayApproval approval, FulfillmentPlan plan, boolean approved) {
        String receiver = StringUtils.hasText(approval.getRequesterName())
                ? "申请人:" + approval.getRequesterName()
                : "申请人";
        String content = clip("延期审批" + (approved ? "通过" : "驳回") + "：节点[" + plan.getNodeName()
                + "]，申请日期 " + Objects.toString(approval.getRequestedDueDate(), "-")
                + (approved ? "" : "，驳回原因：" + Objects.toString(approval.getRejectReason(), "")), 500);
        insertReminder(plan, approved ? "DELAY_APPROVED" : "DELAY_REJECTED", receiver, content);
    }

    private void insertReminder(FulfillmentPlan plan, String level, String receiver, String content) {
        if (!isReminderWarningLevel(level)) {
            return;
        }
        ReminderRecord record = new ReminderRecord();
        record.setPlanId(plan.getPlanId());
        record.setContractId(plan.getContractId());
        record.setReminderLevel(level);
        record.setReminderDate(LocalDate.now());
        record.setChannel("IN_APP");
        record.setReceiver(receiver);
        record.setContent(content);
        record.setSendStatus("SENT");
        record.setSentAt(LocalDateTime.now());
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        record.setDeleted(0);
        reminderMapper.insert(record);
    }

    private void syncPaymentPlanStatus(PaymentPlan plan) {
        syncPaymentPlanStatusFields(plan);
        plan.setUpdatedAt(LocalDateTime.now());
        paymentPlanMapper.updateById(plan);
    }

    private void syncPaymentPlanStatusFields(PaymentPlan plan) {
        if (plan == null) {
            return;
        }
        boolean conditionMet = paymentConditionMet(plan);
        plan.setConditionStatus(conditionMet ? "SATISFIED" : "PENDING");
        BigDecimal planned = money(plan.getPlannedAmount());
        BigDecimal paid = plan.getPaymentPlanId() == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : paidAmount(plan.getPaymentPlanId());
        BigDecimal unpaid = planned.subtract(paid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        long overdueDays = plan.getDueDate() != null && plan.getDueDate().isBefore(LocalDate.now()) && unpaid.compareTo(BigDecimal.ZERO) > 0
                ? ChronoUnit.DAYS.between(plan.getDueDate(), LocalDate.now())
                : 0L;
        plan.setStatus(paymentStatus(plan, planned, paid, overdueDays, conditionMet));
    }

    private FulfillmentPlan assertPlanAccess(Long planId) {
        FulfillmentPlan plan = requirePlan(planId);
        contractService.assertCanAccess(plan.getContractId());
        return plan;
    }

    private FulfillmentPlan assertPlanMaintainAccess(Long planId) {
        FulfillmentPlan plan = assertPlanAccess(planId);
        ContractMain contract = contractService.findContract(plan.getContractId());
        if (!canMaintainPlan(plan, contract)) {
            throw new SecurityException("无权维护该履约节点");
        }
        return plan;
    }

    private FulfillmentPlan assertPlanDeptAuditAccess(Long planId) {
        FulfillmentPlan plan = assertPlanAccess(planId);
        ContractMain contract = contractService.findContract(plan.getContractId());
        String role = SecurityContext.roleCode();
        if ("ADMIN".equals(role)) {
            return plan;
        }
        if ("DEPT_LEADER".equals(role)
                && SecurityContext.deptId() != null
                && SecurityContext.deptId().equals(contract.getDeptId())) {
            return plan;
        }
        throw new SecurityException("仅部门主管或管理员可审核该节点");
    }

    private FulfillmentPlan requirePlan(Long planId) {
        FulfillmentPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new IllegalArgumentException("plan not found");
        }
        return plan;
    }

    private void assertContractMaintainAccess(ContractMain contract) {
        contractService.assertCanAccess(contract.getContractId());
        if (!canMaintainContract(contract)) {
            throw new SecurityException("无权维护该合同的履约节点");
        }
    }

    private boolean canMaintainPlan(FulfillmentPlan plan, ContractMain contract) {
        return canMaintainContract(contract)
                || Objects.equals(plan.getOwnerName(), SecurityContext.username());
    }

    private boolean canMaintainContract(ContractMain contract) {
        if (contract == null) {
            return false;
        }
        String role = SecurityContext.roleCode();
        if ("ADMIN".equals(role)) {
            return true;
        }
        if ("DEPT_LEADER".equals(role)) {
            return SecurityContext.deptId() != null && SecurityContext.deptId().equals(contract.getDeptId());
        }
        if ("USER".equals(role)) {
            return SecurityContext.userId() != null && SecurityContext.userId().equals(contract.getOwnerId());
        }
        return false;
    }

    private void assertVoucherReviewAccess(FulfillmentPlan plan) {
        ContractMain contract = contractService.findContract(plan.getContractId());
        String role = SecurityContext.roleCode();
        if ("ADMIN".equals(role)) {
            return;
        }
        if ("DEPT_LEADER".equals(role)
                && SecurityContext.deptId() != null
                && SecurityContext.deptId().equals(contract.getDeptId())) {
            return;
        }
        if ("FINANCE".equals(role)
                && "PAYMENT".equals(plan.getPlanType())
                && canReadPaymentNode(contract)) {
            return;
        }
        throw new SecurityException("无权复核该履约凭证");
    }

    private boolean canReadPaymentNode(ContractMain contract) {
        if (contract == null) {
            return false;
        }
        String scope = SecurityContext.dataScope();
        if ("ALL".equals(scope)) {
            return true;
        }
        if ("DEPT".equals(scope)) {
            return SecurityContext.deptId() != null && SecurityContext.deptId().equals(contract.getDeptId());
        }
        return SecurityContext.userId() != null && SecurityContext.userId().equals(contract.getOwnerId());
    }

    private FulfillmentDeliverable assertDeliverableAccess(Long deliverableId) {
        FulfillmentDeliverable item = deliverableMapper.selectById(deliverableId);
        if (item == null) {
            throw new IllegalArgumentException("deliverable not found");
        }
        contractService.assertCanAccess(item.getContractId());
        return item;
    }

    private FulfillmentDeliverable assertDeliverableMaintainAccess(Long deliverableId) {
        FulfillmentDeliverable item = assertDeliverableAccess(deliverableId);
        ContractMain contract = contractService.findContract(item.getContractId());
        if (!canMaintainContract(contract)) {
            throw new SecurityException("无权维护该交付物");
        }
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

    private void assertPaymentPlanDeletable(long paymentRecordCount, long invoiceCount) {
        if (paymentRecordCount > 0 || invoiceCount > 0) {
            throw new IllegalStateException("该付款计划已有到账记录或发票材料，不能直接删除；请先删除相关记录");
        }
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
                normalizeStatus(plan.getStatus()),
                plan.getProgress(),
                plan.getActualCompletedDate(),
                plan.getOwnerName(),
                plan.getSourceType(),
                plan.getSourceClause(),
                plan.getAiConfidence(),
                (plan.getAiExtracted() != null && plan.getAiExtracted() == 1) || "AI".equals(plan.getSourceType()),
                StringUtils.hasText(plan.getConfirmStatus()) ? plan.getConfirmStatus() : "CONFIRMED",
                plan.getOverdueDays() == null ? 0 : plan.getOverdueDays(),
                StringUtils.hasText(plan.getDelayStatus()) ? plan.getDelayStatus() : "NONE",
                plan.getDelayRequestedDueDate(),
                plan.getDelayReason(),
                plan.getDelayRequestedBy(),
                plan.getDelayRequestedAt(),
                plan.getDelayConfirmedBy(),
                plan.getDelayConfirmedAt(),
                plan.getDelayRejectedBy(),
                plan.getDelayRejectedAt(),
                plan.getDelayRejectReason(),
                warningLevel(plan),
                daysLeft,
                plan.getRemark(),
                plan.getUpdatedAt()
        );
    }

    private FulfillmentProgressLogVO toProgressLogVo(FulfillmentProgressLog log) {
        return new FulfillmentProgressLogVO(
                log.getLogId(),
                log.getPlanId(),
                log.getContractId(),
                progressOperationText(log.getOperation()),
                StringUtils.hasText(log.getBeforeStatus()) ? normalizeStatus(log.getBeforeStatus()) : null,
                StringUtils.hasText(log.getAfterStatus()) ? normalizeStatus(log.getAfterStatus()) : null,
                log.getBeforeValue(),
                log.getAfterValue(),
                log.getOperatorId(),
                log.getOperatorName(),
                log.getOperateTime(),
                log.getRemark(),
                log.getClientIp()
        );
    }

    private String progressOperationText(String operation) {
        if (!StringUtils.hasText(operation)) {
            return "";
        }
        String trimmed = operation.trim();
        String mapped = PROGRESS_OPERATION_TEXT.get(trimmed);
        if (mapped != null) {
            return mapped;
        }
        String upper = trimmed.toUpperCase();
        mapped = PROGRESS_OPERATION_TEXT.get(upper);
        if (mapped != null) {
            return mapped;
        }
        if (!trimmed.matches("[A-Z0-9_]+")) {
            return trimmed;
        }
        if (trimmed.startsWith("FULFILLMENT_DELIVERABLE")) {
            return "交付物操作";
        }
        if (trimmed.startsWith("FULFILLMENT_PLAN")) {
            return "履约节点操作";
        }
        if (trimmed.startsWith("FULFILLMENT_VOUCHER") || trimmed.startsWith("VOUCHER")) {
            return "凭证操作";
        }
        if (trimmed.startsWith("OVERDUE")) {
            return "逾期处置";
        }
        if (trimmed.startsWith("DELAY")) {
            return "延期处理";
        }
        return "系统操作";
    }

    private FulfillmentVoucherVO toVoucherVo(FulfillmentVoucher voucher) {
        FulfillmentPlan plan = planMapper.selectById(voucher.getPlanId());
        FileInfo fileInfo = requireFile(voucher.getFileId());
        return new FulfillmentVoucherVO(
                voucher.getVoucherId(),
                voucher.getPlanId(),
                voucher.getContractId(),
                plan != null ? plan.getNodeName() : "",
                plan != null ? plan.getPlanType() : "",
                fileInfo.getFileId(),
                fileInfo.getFileName(),
                fileInfo.getFileType(),
                fileInfo.getSize(),
                voucher.getVoucherType(),
                voucher.getReviewStatus(),
                voucher.getUploadedBy(),
                voucher.getUploadedByName(),
                voucher.getUploadedAt(),
                voucher.getReviewerId(),
                voucher.getReviewerName(),
                voucher.getReviewedAt(),
                voucher.getRemark(),
                "/api/fulfillment/vouchers/" + voucher.getVoucherId() + "/download"
        );
    }

    private FulfillmentDelayApprovalVO toDelayApprovalVo(FulfillmentDelayApproval approval) {
        FulfillmentPlan plan = planMapper.selectById(approval.getPlanId());
        ContractMain contract = contractMapper.selectById(approval.getContractId());
        return new FulfillmentDelayApprovalVO(
                approval.getApprovalId(),
                approval.getPlanId(),
                approval.getContractId(),
                contract != null ? contract.getContractNo() : "",
                contract != null ? contract.getTitle() : "",
                plan != null ? plan.getNodeName() : "",
                approval.getOriginalDueDate(),
                approval.getRequestedDueDate(),
                approval.getDelayReason(),
                approval.getStatus(),
                approval.getRequesterName(),
                approval.getRequestedAt(),
                approval.getApproverName(),
                approval.getApprovedAt(),
                approval.getRejectReason(),
                approval.getNoticeStatus()
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
        FulfillmentPlan plan = item.getPlanId() == null ? null : planMapper.selectById(item.getPlanId());
        FileInfo fileInfo = item.getFileId() == null ? null : fileInfoMapper.selectById(item.getFileId());
        return new DeliverableVO(
                item.getDeliverableId(),
                item.getPlanId(),
                item.getContractId(),
                contract != null ? contract.getTitle() : "",
                plan != null ? plan.getNodeName() : "",
                plan != null ? plan.getPlanType() : "",
                item.getDeliverableType(),
                item.getDeliverableName(),
                item.getStageName(),
                item.getConfirmMethod(),
                normalizeDeliverableStatus(item.getStatus()),
                StringUtils.hasText(item.getConfirmStatus()) ? item.getConfirmStatus() : "CONFIRMED",
                item.getSourceClause(),
                item.getAiConfidence(),
                Integer.valueOf(1).equals(item.getAiExtracted()),
                item.getConfirmed() != null && item.getConfirmed() == 1,
                item.getConfirmer(),
                item.getConfirmedAt(),
                Integer.valueOf(1).equals(item.getAcceptancePassed()),
                item.getAcceptedBy(),
                item.getAcceptedAt(),
                item.getSubmittedBy(),
                item.getSubmittedAt(),
                item.getReviewerName(),
                item.getReviewedAt(),
                item.getReviewComment(),
                item.getSubmissionVersion() == null ? 0 : item.getSubmissionVersion(),
                item.getFileId(),
                fileInfo != null ? fileInfo.getFileName() : "",
                item.getFileId() == null ? "" : "/api/fulfillment/deliverables/" + item.getDeliverableId() + "/file",
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
        boolean conditionMet = paymentConditionMet(plan);
        long overdueDays = plan.getDueDate() != null && plan.getDueDate().isBefore(LocalDate.now()) && unpaid.compareTo(BigDecimal.ZERO) > 0
                ? ChronoUnit.DAYS.between(plan.getDueDate(), LocalDate.now())
                : 0L;
        BigDecimal rate = plan.getPenaltyRate() != null ? plan.getPenaltyRate() : DEFAULT_PENALTY_RATE;
        BigDecimal penaltyAmount = overdueDays <= 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : unpaid.multiply(rate).multiply(BigDecimal.valueOf(overdueDays)).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
        String status = paymentStatus(plan, planned, paid, overdueDays, conditionMet);
        FulfillmentPlan linkedPlan = plan.getFulfillmentPlanId() == null ? null : planMapper.selectById(plan.getFulfillmentPlanId());
        return new PaymentPlanVO(
                plan.getPaymentPlanId(),
                plan.getContractId(),
                plan.getFulfillmentPlanId(),
                contract != null ? contract.getTitle() : "",
                linkedPlan != null ? linkedPlan.getNodeName() : "",
                plan.getPhaseName(),
                plan.getPercentage(),
                planned,
                paid,
                unpaid,
                plan.getDueDate(),
                plan.getPayee(),
                plan.getPaymentCondition(),
                normalizeConditionType(plan.getConditionType()),
                conditionMet ? "SATISFIED" : "PENDING",
                plan.getPrerequisiteDelivery(),
                conditionMet,
                rate,
                overdueDays,
                penaltyAmount,
                status,
                responsibilityHint(status, overdueDays, conditionMet),
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
                record.getBankSerialNo(),
                record.getHandlerName(),
                record.getVoucherFileId(),
                paymentRecordFileName(record),
                record.getVoucherFileId() == null ? "" : "/api/fulfillment/payments/records/" + record.getPaymentRecordId() + "/voucher",
                record.getPayer(),
                record.getReceiver(),
                record.getRemark()
        );
    }

    private List<ReminderRecordVO> toReminderVos(List<ReminderRecord> records) {
        Map<Long, FulfillmentPlan> plans = planMap(records.stream().map(ReminderRecord::getPlanId).toList());
        Map<Long, ContractMain> contracts = contractMap(records.stream().map(ReminderRecord::getContractId).toList());
        return records.stream()
                .filter(record -> isReminderWarningLevel(record.getReminderLevel()))
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

    private boolean prerequisiteCompleted(PaymentPlan plan, boolean requireAcceptance) {
        List<DeliverableVO> deliverables = listDeliverables(plan.getContractId());
        if (!StringUtils.hasText(plan.getPrerequisiteDelivery())) {
            return !requireAcceptance || deliverables.stream().anyMatch(item -> Boolean.TRUE.equals(item.acceptancePassed()));
        }
        for (String name : plan.getPrerequisiteDelivery().split("[,，、]")) {
            String normalized = name.trim();
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            boolean matched = deliverables.stream()
                    .anyMatch(item -> (requireAcceptance
                            ? Boolean.TRUE.equals(item.acceptancePassed())
                            : Boolean.TRUE.equals(item.confirmed()))
                            && (normalized.equals(item.deliverableName()) || normalized.equals(item.deliverableType())));
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private boolean paymentConditionMet(PaymentPlan plan) {
        String conditionType = normalizeConditionType(plan.getConditionType());
        return switch (conditionType) {
            case "DELIVERABLE" -> prerequisiteCompleted(plan, false);
            case "ACCEPTANCE" -> prerequisiteCompleted(plan, true);
            case "INVOICE" -> invoiceReady(plan);
            default -> true;
        };
    }

    private String normalizeConditionType(String conditionType) {
        if (!StringUtils.hasText(conditionType)) {
            return "NONE";
        }
        String value = conditionType.trim().toUpperCase();
        if (List.of("NONE", "DELIVERABLE", "ACCEPTANCE", "INVOICE").contains(value)) {
            return value;
        }
        return "NONE";
    }

    private boolean invoiceReady(PaymentPlan plan) {
        if (plan == null || jdbcTemplate == null) {
            return false;
        }
        BigDecimal required = money(plan.getPlannedAmount());
        BigDecimal invoiced = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(invoice_amount), 0)
                FROM invoice_record
                WHERE payment_plan_id = ?
                  AND invoice_status IN ('RECEIVED', 'VERIFIED', 'VALID')
                  AND is_deleted = 0
                """, BigDecimal.class, plan.getPaymentPlanId());
        if (required.compareTo(BigDecimal.ZERO) <= 0) {
            Long count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM invoice_record
                    WHERE payment_plan_id = ?
                      AND invoice_status IN ('RECEIVED', 'VERIFIED', 'VALID')
                      AND is_deleted = 0
                    """, Long.class, plan.getPaymentPlanId());
            return count != null && count > 0;
        }
        return money(invoiced).compareTo(required) >= 0;
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

    private String paymentStatus(PaymentPlan plan, BigDecimal planned, BigDecimal paid, long overdueDays, boolean conditionMet) {
        if (plan != null && "SUSPENDED".equals(plan.getStatus())) {
            return "SUSPENDED";
        }
        if (planned.compareTo(BigDecimal.ZERO) > 0 && paid.compareTo(planned) >= 0) {
            return "PAID";
        }
        if (overdueDays > 0) {
            return "OVERDUE";
        }
        if (paid.compareTo(BigDecimal.ZERO) > 0) {
            return "PARTIAL_PAID";
        }
        if (!conditionMet) {
            return "WAIT_CONDITION";
        }
        return "READY_TO_PAY";
    }

    private String responsibilityHint(String status, long overdueDays, boolean prerequisiteCompleted) {
        String hint;
        if ("PAID".equals(status)) {
            hint = "已到账，无需责任提示";
        } else if ("WAIT_CONDITION".equals(status)) {
            hint = "付款条件未满足，暂不允许登记付款";
        } else if (overdueDays <= 0) {
            hint = "未到付款期限，持续跟踪";
        } else if (prerequisiteCompleted) {
            hint = "到期未付款且前置交付已完成，提示甲方延迟支付";
        } else {
            hint = "前置交付未完成，提示待人工判断乙方履约责任";
        }
        return hint + "（" + RESPONSIBILITY_DISCLAIMER + "）";
    }

    private String normalizeInvoiceStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "RECEIVED";
        }
        String value = status.trim().toUpperCase();
        if (List.of("DRAFT", "RECEIVED", "VERIFIED", "VALID", "INVALID", "VOID").contains(value)) {
            return value;
        }
        return "RECEIVED";
    }

    private String paymentRecordFileName(PaymentRecord record) {
        if (record == null || record.getVoucherFileId() == null) {
            return "";
        }
        FileInfo fileInfo = fileInfoMapper.selectById(record.getVoucherFileId());
        return fileInfo != null ? fileInfo.getFileName() : "";
    }

    private String paymentPlanSnapshot(PaymentPlan plan) {
        if (plan == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("paymentPlanId", plan.getPaymentPlanId());
        snapshot.put("contractId", plan.getContractId());
        snapshot.put("fulfillmentPlanId", plan.getFulfillmentPlanId());
        snapshot.put("phaseName", Objects.toString(plan.getPhaseName(), ""));
        snapshot.put("plannedAmount", money(plan.getPlannedAmount()).toPlainString());
        snapshot.put("dueDate", plan.getDueDate() == null ? "" : plan.getDueDate().toString());
        snapshot.put("payee", Objects.toString(plan.getPayee(), ""));
        snapshot.put("paymentCondition", Objects.toString(plan.getPaymentCondition(), ""));
        snapshot.put("conditionType", Objects.toString(plan.getConditionType(), ""));
        snapshot.put("conditionStatus", Objects.toString(plan.getConditionStatus(), ""));
        snapshot.put("status", Objects.toString(plan.getStatus(), ""));
        snapshot.put("remark", Objects.toString(plan.getRemark(), ""));
        try {
            return objectMapper != null ? objectMapper.writeValueAsString(snapshot) : snapshot.toString();
        } catch (Exception ex) {
            return snapshot.toString();
        }
    }

    private String paymentRecordSnapshot(PaymentRecord record) {
        if (record == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("paymentRecordId", record.getPaymentRecordId());
        snapshot.put("paymentPlanId", record.getPaymentPlanId());
        snapshot.put("contractId", record.getContractId());
        snapshot.put("paidAmount", money(record.getPaidAmount()).toPlainString());
        snapshot.put("paidDate", record.getPaidDate() == null ? "" : record.getPaidDate().toString());
        snapshot.put("bankSerialNo", Objects.toString(record.getBankSerialNo(), ""));
        snapshot.put("handlerName", Objects.toString(record.getHandlerName(), ""));
        snapshot.put("voucherFileId", record.getVoucherFileId());
        snapshot.put("payer", Objects.toString(record.getPayer(), ""));
        snapshot.put("receiver", Objects.toString(record.getReceiver(), ""));
        snapshot.put("remark", Objects.toString(record.getRemark(), ""));
        try {
            return objectMapper != null ? objectMapper.writeValueAsString(snapshot) : snapshot.toString();
        } catch (Exception ex) {
            return snapshot.toString();
        }
    }

    private String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private FulfillmentVoucher requireVoucher(Long voucherId) {
        FulfillmentVoucher voucher = voucherMapper.selectById(voucherId);
        if (voucher == null) {
            throw new IllegalArgumentException("voucher not found");
        }
        return voucher;
    }

    private FulfillmentDelayApproval requireDelayApproval(Long approvalId) {
        if (delayApprovalMapper == null) {
            throw new IllegalStateException("delay approval mapper is not available");
        }
        FulfillmentDelayApproval approval = delayApprovalMapper.selectById(approvalId);
        if (approval == null) {
            throw new IllegalArgumentException("delay approval not found");
        }
        return approval;
    }

    private FileInfo requireFile(Long fileId) {
        FileInfo fileInfo = fileInfoMapper.selectById(fileId);
        if (fileInfo == null) {
            throw new IllegalArgumentException("file not found");
        }
        return fileInfo;
    }

    private FileInfo findOrCreateFile(FileStorageService.StoredFile stored, String originalName) {
        FileInfo existing = fileInfoMapper.selectOne(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getSha256, stored.sha256())
                .last("LIMIT 1"));
        if (existing != null) {
            if (!Objects.equals(existing.getObjectKey(), stored.objectKey())) {
                existing.setObjectKey(stored.objectKey());
                existing.setUpdatedAt(LocalDateTime.now());
                fileInfoMapper.updateById(existing);
            }
            return existing;
        }
        FileInfo fileInfo = new FileInfo();
        fileInfo.setObjectKey(stored.objectKey());
        fileInfo.setFileName(StringUtils.hasText(originalName) ? originalName : "voucher." + stored.fileType());
        fileInfo.setFileType(stored.fileType());
        fileInfo.setSize(stored.size());
        fileInfo.setSha256(stored.sha256());
        fileInfo.setCreatedBy(currentUserLabel());
        fileInfo.setCreatedAt(LocalDateTime.now());
        fileInfo.setUpdatedAt(LocalDateTime.now());
        fileInfo.setDeleted(0);
        fileInfo.setVersion(1);
        fileInfoMapper.insert(fileInfo);
        return fileInfo;
    }

    private void validateVoucherFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择履约凭证文件");
        }
        String name = Objects.toString(file.getOriginalFilename(), "").toLowerCase();
        boolean allowed = name.endsWith(".pdf")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".png")
                || name.endsWith(".doc")
                || name.endsWith(".docx")
                || name.endsWith(".xls")
                || name.endsWith(".xlsx");
        if (!allowed) {
            throw new IllegalArgumentException("履约凭证仅支持 PDF、图片、Word 或 Excel 文件");
        }
        if (file.getSize() > 50L * 1024 * 1024) {
            throw new IllegalArgumentException("履约凭证不能超过 50MB");
        }
    }

    private String contentDisposition(String filename) {
        String safeName = filename == null ? "voucher" : filename.replace("\"", "'");
        return ContentDisposition.attachment()
                .filename(safeName, StandardCharsets.UTF_8)
                .build()
                .toString();
    }

    private String currentUserLabel() {
        Long userId = SecurityContext.userId();
        if (userId != null) {
            return String.valueOf(userId);
        }
        return StringUtils.hasText(SecurityContext.username()) ? SecurityContext.username() : "system";
    }

    private void writeDeliverableLog(String operation,
                                     FulfillmentDeliverable item,
                                     String beforeContent,
                                     String afterContent,
                                     String clientIp) {
        if (item == null) {
            return;
        }
        writeOperationLog(operation, "FULFILLMENT_DELIVERABLE", item.getDeliverableId(),
                beforeContent, afterContent, "SUCCESS");
        if (item.getPlanId() != null && progressLogMapper != null) {
            FulfillmentPlan plan = planMapper.selectById(item.getPlanId());
            if (plan != null) {
                writeProgressLog(operation, plan.getPlanId(), plan.getContractId(), plan.getStatus(), plan.getStatus(),
                        beforeContent, afterContent, item.getRemark(), clientIp);
            }
        }
    }

    private String deliverableSnapshot(FulfillmentDeliverable item) {
        if (item == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("deliverableId", item.getDeliverableId());
        snapshot.put("planId", item.getPlanId());
        snapshot.put("contractId", item.getContractId());
        snapshot.put("deliverableType", Objects.toString(item.getDeliverableType(), ""));
        snapshot.put("deliverableName", Objects.toString(item.getDeliverableName(), ""));
        snapshot.put("stageName", Objects.toString(item.getStageName(), ""));
        snapshot.put("deliverableStatus", normalizeDeliverableStatus(item.getStatus()));
        snapshot.put("confirmStatus", Objects.toString(item.getConfirmStatus(), ""));
        snapshot.put("confirmed", item.getConfirmed());
        snapshot.put("acceptancePassed", item.getAcceptancePassed());
        snapshot.put("acceptedBy", Objects.toString(item.getAcceptedBy(), ""));
        snapshot.put("acceptedAt", item.getAcceptedAt() == null ? "" : item.getAcceptedAt().toString());
        snapshot.put("submittedBy", Objects.toString(item.getSubmittedBy(), ""));
        snapshot.put("submittedAt", item.getSubmittedAt() == null ? "" : item.getSubmittedAt().toString());
        snapshot.put("reviewerName", Objects.toString(item.getReviewerName(), ""));
        snapshot.put("reviewedAt", item.getReviewedAt() == null ? "" : item.getReviewedAt().toString());
        snapshot.put("reviewComment", Objects.toString(item.getReviewComment(), ""));
        snapshot.put("submissionVersion", item.getSubmissionVersion());
        snapshot.put("sourceClause", Objects.toString(item.getSourceClause(), ""));
        snapshot.put("aiConfidence", item.getAiConfidence() == null ? "" : item.getAiConfidence().toString());
        snapshot.put("aiExtracted", item.getAiExtracted());
        snapshot.put("fileId", item.getFileId());
        snapshot.put("remark", Objects.toString(item.getRemark(), ""));
        try {
            return objectMapper != null ? objectMapper.writeValueAsString(snapshot) : snapshot.toString();
        } catch (Exception ex) {
            return snapshot.toString();
        }
    }

    private String voucherSnapshot(FulfillmentVoucher voucher, FileInfo fileInfo) {
        if (voucher == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("voucherId", voucher.getVoucherId());
        snapshot.put("planId", voucher.getPlanId());
        snapshot.put("contractId", voucher.getContractId());
        snapshot.put("fileId", voucher.getFileId());
        snapshot.put("fileName", fileInfo != null ? fileInfo.getFileName() : "");
        snapshot.put("voucherType", Objects.toString(voucher.getVoucherType(), ""));
        snapshot.put("reviewStatus", Objects.toString(voucher.getReviewStatus(), ""));
        snapshot.put("uploadedBy", voucher.getUploadedBy());
        snapshot.put("uploadedByName", Objects.toString(voucher.getUploadedByName(), ""));
        snapshot.put("reviewerName", Objects.toString(voucher.getReviewerName(), ""));
        snapshot.put("remark", Objects.toString(voucher.getRemark(), ""));
        try {
            return objectMapper != null ? objectMapper.writeValueAsString(snapshot) : snapshot.toString();
        } catch (Exception ex) {
            return snapshot.toString();
        }
    }

    private void writeProgressLog(String operation,
                                  Long planId,
                                  Long contractId,
                                  String beforeStatus,
                                  String afterStatus,
                                  String beforeValue,
                                  String afterValue,
                                  String remark,
                                  String clientIp) {
        if (progressLogMapper == null || planId == null || contractId == null) {
            return;
        }
        FulfillmentProgressLog log = new FulfillmentProgressLog();
        log.setPlanId(planId);
        log.setContractId(contractId);
        log.setOperation(operation);
        log.setBeforeStatus(StringUtils.hasText(beforeStatus) ? normalizeStatus(beforeStatus) : null);
        log.setAfterStatus(StringUtils.hasText(afterStatus) ? normalizeStatus(afterStatus) : null);
        log.setBeforeValue(clip(beforeValue, 4000));
        log.setAfterValue(clip(afterValue, 4000));
        log.setOperatorId(SecurityContext.userId());
        log.setOperatorName(StringUtils.hasText(SecurityContext.username()) ? SecurityContext.username() : "system");
        log.setOperateTime(LocalDateTime.now());
        log.setRemark(clip(remark, 500));
        log.setClientIp(StringUtils.hasText(clientIp) ? clientIp : "127.0.0.1");
        progressLogMapper.insert(log);
    }

    private void writePlanLog(String operation, Long planId, String beforeContent, String afterContent, String result) {
        writeOperationLog(operation, "FULFILLMENT_PLAN", planId, beforeContent, afterContent, result);
    }

    private void writeOperationLog(String operation,
                                   String targetType,
                                   Long targetId,
                                   String beforeContent,
                                   String afterContent,
                                   String result) {
        if (operationLogMapper == null) {
            return;
        }
        OperationLog log = new OperationLog();
        log.setUserId(SecurityContext.userId());
        log.setOperation(operation);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setIp("127.0.0.1");
        log.setResult(result);
        log.setBeforeContent(clip(beforeContent, 4000));
        log.setAfterContent(clip(afterContent, 4000));
        log.setCreatedAt(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    private String planSnapshot(FulfillmentPlan plan) {
        if (plan == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("planId", plan.getPlanId());
        snapshot.put("contractId", plan.getContractId());
        snapshot.put("nodeName", Objects.toString(plan.getNodeName(), ""));
        snapshot.put("planType", Objects.toString(plan.getPlanType(), ""));
        snapshot.put("dueDate", plan.getDueDate() == null ? "" : plan.getDueDate().toString());
        snapshot.put("status", Objects.toString(normalizeStatus(plan.getStatus()), ""));
        snapshot.put("progress", plan.getProgress() == null ? 0 : plan.getProgress());
        snapshot.put("actualCompletedDate", plan.getActualCompletedDate() == null ? "" : plan.getActualCompletedDate().toString());
        snapshot.put("ownerName", Objects.toString(plan.getOwnerName(), ""));
        snapshot.put("sourceType", Objects.toString(plan.getSourceType(), ""));
        snapshot.put("sourceClause", Objects.toString(plan.getSourceClause(), ""));
        snapshot.put("aiConfidence", plan.getAiConfidence() == null ? "" : plan.getAiConfidence().toPlainString());
        snapshot.put("aiExtracted", plan.getAiExtracted() == null ? 0 : plan.getAiExtracted());
        snapshot.put("confirmStatus", Objects.toString(plan.getConfirmStatus(), ""));
        snapshot.put("delayStatus", Objects.toString(plan.getDelayStatus(), ""));
        snapshot.put("delayRejectReason", Objects.toString(plan.getDelayRejectReason(), ""));
        snapshot.put("remark", Objects.toString(plan.getRemark(), ""));
        try {
            return objectMapper != null ? objectMapper.writeValueAsString(snapshot) : snapshot.toString();
        } catch (Exception ex) {
            return snapshot.toString();
        }
    }

    private boolean hasAiMetadata(FulfillmentPlan plan) {
        return plan != null
                && plan.getAiConfidence() != null
                && plan.getAiExtracted() != null
                && plan.getAiExtracted() == 1
                && StringUtils.hasText(plan.getConfirmStatus());
    }

    private String warningLevel(FulfillmentPlan plan) {
        String status = plan == null ? null : normalizeStatus(plan.getStatus());
        if (plan == null || CLOSED_STATUS.contains(status)) {
            return "NONE";
        }
        if (STATUS_PENDING_CONFIRM.equals(status) || STATUS_PENDING_CONFIRM.equals(plan.getConfirmStatus())) {
            return "NONE";
        }
        if (plan.getDueDate() == null) {
            return "NORMAL";
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(), plan.getDueDate());
        if (days < 0 || STATUS_OVERDUE.equals(status)) {
            return "OVERDUE";
        }
        if (days == LEVEL3_WARNING_DAYS) {
            return "LEVEL3";
        }
        if (days == LEVEL2_WARNING_DAYS) {
            return "LEVEL2";
        }
        if (days == LEVEL1_WARNING_DAYS) {
            return "LEVEL1";
        }
        return "NORMAL";
    }

    private List<ReminderDelivery> reminderDeliveries(FulfillmentPlanVO plan, ContractMain contract) {
        String receiver = clip(String.join("；", reminderReceivers(plan, contract)), 500);
        return reminderChannels(plan.warningLevel()).stream()
                .map(channel -> new ReminderDelivery(channel, receiver))
                .toList();
    }

    private List<String> reminderChannels(String warningLevel) {
        return switch (warningLevel) {
            case "LEVEL1" -> List.of("IN_APP");
            case "LEVEL2" -> List.of("IN_APP", "EMAIL", "WECHAT");
            case "LEVEL3", "OVERDUE" -> List.of("IN_APP", "EMAIL", "WECHAT", "SMS");
            default -> List.of();
        };
    }

    private List<String> reminderReceivers(FulfillmentPlanVO plan, ContractMain contract) {
        Set<String> receivers = new LinkedHashSet<>();
        receivers.add(ownerReceiver(plan, contract));
        if ("LEVEL2".equals(plan.warningLevel())
                || "LEVEL3".equals(plan.warningLevel())
                || "OVERDUE".equals(plan.warningLevel())) {
            receivers.addAll(roleReceivers("DEPT_LEADER", contract != null ? contract.getDeptId() : null, "部门主管"));
        }
        if ("LEVEL3".equals(plan.warningLevel()) || "OVERDUE".equals(plan.warningLevel())) {
            receivers.addAll(roleReceivers("EXECUTIVE", null, "企业高管"));
        }
        return new ArrayList<>(receivers);
    }

    private String ownerReceiver(FulfillmentPlanVO plan, ContractMain contract) {
        String owner = plan != null ? plan.ownerName() : "";
        if (!StringUtils.hasText(owner) && contract != null) {
            owner = usernameById(contract.getOwnerId());
        }
        if (!StringUtils.hasText(owner)) {
            owner = SecurityContext.username();
        }
        return "合同负责人:" + (StringUtils.hasText(owner) ? owner : "合同负责人");
    }

    private List<String> roleReceivers(String roleCode, Long deptId, String fallbackLabel) {
        if (jdbcTemplate == null) {
            return List.of(fallbackLabel);
        }
        try {
            List<String> names = jdbcTemplate.queryForList("""
                    SELECT DISTINCT u.username
                    FROM user_info u
                    INNER JOIN role_info r ON r.role_id = u.role_id AND r.is_deleted = 0
                    WHERE u.is_deleted = 0
                      AND (u.status IS NULL OR u.status = 1)
                      AND r.role_code = ?
                      AND (? IS NULL OR u.dept_id = ?)
                    ORDER BY u.username
                    """, String.class, roleCode, deptId, deptId);
            List<String> receivers = names.stream()
                    .filter(StringUtils::hasText)
                    .map(name -> fallbackLabel + ":" + name.trim())
                    .toList();
            return receivers.isEmpty() ? List.of(fallbackLabel) : receivers;
        } catch (Exception ex) {
            log.warn("Resolve reminder receivers failed. roleCode={}, deptId={}, reason={}",
                    roleCode, deptId, ex.getMessage());
            return List.of(fallbackLabel);
        }
    }

    private String usernameById(Long userId) {
        if (userId == null || jdbcTemplate == null) {
            return "";
        }
        try {
            List<String> names = jdbcTemplate.queryForList("""
                    SELECT username
                    FROM user_info
                    WHERE user_id = ?
                      AND is_deleted = 0
                    LIMIT 1
                    """, String.class, userId);
            return names.stream().filter(StringUtils::hasText).findFirst().orElse("");
        } catch (Exception ex) {
            log.warn("Resolve owner username failed. userId={}, reason={}", userId, ex.getMessage());
            return "";
        }
    }

    private boolean reminderAlreadySent(FulfillmentPlanVO plan, LocalDate reminderDate, String channel) {
        Long count = reminderMapper.selectCount(new LambdaQueryWrapper<ReminderRecord>()
                .eq(ReminderRecord::getPlanId, plan.planId())
                .eq(ReminderRecord::getReminderLevel, plan.warningLevel())
                .eq(ReminderRecord::getChannel, channel)
                .eq(ReminderRecord::getReminderDate, reminderDate));
        return count != null && count > 0;
    }

    private String reminderContent(FulfillmentPlanVO plan) {
        String daysText = plan.daysLeft() == null
                ? "待确认"
                : (plan.daysLeft() < 0 ? "已逾期" + Math.abs(plan.daysLeft()) + "天" : "剩余" + plan.daysLeft() + "天");
        String action = switch (plan.warningLevel()) {
            case "LEVEL1" -> "一级预警，请合同负责人关注节点进展";
            case "LEVEL2" -> "二级预警，节点临近到期，请尽快处理";
            case "LEVEL3" -> "三级预警，最后提醒，请立即处理";
            case "OVERDUE" -> "逾期通知，请处理或填写延期说明";
            default -> "履约提醒";
        };
        return clip("合同【" + plan.contractTitle() + "】履约节点【" + plan.nodeName() + "】"
                + daysText + "，" + action + "，处理入口：履约预警看板/合同详情页", 500);
    }

    private record ReminderDelivery(String channel, String receiver) {
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
