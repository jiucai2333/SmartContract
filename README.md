# SmartContract

> 基于大模型的中小企业合同全生命周期管理系统

Spring Boot 3.5 · Vue 3 · MyBatis-Plus · MySQL 8.0 · Redis · 通义千问 · PaddleOCR · 法大大电子签章

覆盖合同全生命周期：**起草 → AI 辅助 → 审批 → 签章 → 归档 → 履约追踪 → 完成**

---

## 目录

- [功能概览](#功能概览)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [数据库表](#数据库表)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [前端页面](#前端页面)
- [后端模块](#后端模块)
- [API 参考](#api-参考)
- [安全模型](#安全模型)
- [Git 提交说明](#git-提交说明)

---

## 功能概览

### 合同生命周期

```
DRAFT → APPROVING → APPROVED → SIGNING → ARCHIVED → EXECUTING → COMPLETED
                                                         ↘ EXPIRED / TERMINATED
```

| 阶段 | 功能 | 说明 |
|------|------|------|
| 起草 | 在线编辑器、AI 辅助生成、模板填充 | 支持富文本、Markdown、DOCX 导入导出 |
| 审批 | 三级审批流程 | 普通 / 重大（≥10万）/ 超阈值（≥50万） |
| 签章 | 电子签章登记 | 集成法大大 FASC v5 API |
| 归档 | 归档编号生成、版本锁定 | 归档后禁止编辑 |
| 履约 | 交付物追踪、付款台账 | 逾期预警、分级提醒 |
| 区块链 | 合同哈希上链存证 | 操作可追溯 |

### AI 能力

| 能力 | 模型 | 说明 |
|------|------|------|
| AI 起草 | 通义千问 qwen-plus | SSE 流式输出合同正文 |
| 字段识别 | 通义千问 | 从正文自动提取可填充字段 |
| 风险审查 | 通义千问 | 逐条风险识别、等级评定、报告落库 |
| 合规审查 | 通义千问 | AI 合规性分析 |
| OCR 识别 | PaddleOCR-VL-1.6 | 版式还原、表格识别、方向校正 |

### 文档处理

- **格式支持**：PDF、DOCX、DOC、Markdown、HTML
- **OCR**：PaddleOCR 云服务 / 阿里云 OCR，支持版式还原
- **文档解析**：Apache PDFBox + POI 文本提取
- **导出**：DOCX 合同导出、风险报告导出、Markdown 导入导出

### 安全与审计

- JWT（HMAC-SHA256）认证 + BCrypt 密码哈希
- `@RequireRole` 声明式角色控制（6 级角色）
- SELF / DEPT / ALL 三级数据权限
- `@AuditOperation` 操作审计（失败不阻塞业务）
- 敏感数据脱敏（`SensitiveDataMasker`）
- 安全事件采集与查询

---

## 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 17 |
| 框架 | Spring Boot | 3.5.14 |
| Web | Spring MVC + Jakarta Validation | — |
| AOP | Spring Boot AOP | — |
| ORM | MyBatis-Plus | 3.5.12 |
| 数据库 | MySQL | 8.0 |
| 缓存 | Spring Data Redis | — |
| 认证 | 自签 JWT + BCrypt | — |
| AI | 通义千问（OpenAI 兼容接口） | qwen-plus |
| OCR | PaddleOCR 云服务 | PaddleOCR-VL-1.6 |
| 文档 | Apache POI / PDFBox / Jsoup | 5.4.1 / 3.0.5 / 1.20.1 |
| 签章 | 法大大 FASC v5 API | — |
| 工具 | Lombok | 1.18.46 |
| 前端 | Vue 3 + Vite + Vue Router + Pinia | — |
| 构建 | Maven + Vite | — |

---

## 项目结构

```
SmartContract/
├── backend/                           # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/cupk/smartcontract/
│       │   ├── SmartContractApplication.java
│       │   ├── common/                # Result 响应体、RoleEnum、SecurityEventType
│       │   ├── config/                # MyBatis-Plus、Qwen、OCR、存储、签章、外部集成配置
│       │   ├── controller/            # REST Controller（25 个）
│       │   ├── dto/                   # 请求 / 响应 DTO
│       │   ├── entity/                # 数据库实体（26 个表）
│       │   ├── mapper/                # MyBatis-Plus Mapper
│       │   ├── security/              # JWT 拦截、@RequireRole、审计切面、脱敏
│       │   └── service/               # 业务 Service（34 个）
│       │       └── signature/         # 签章服务（法大大 / 本地提供者）
│       └── resources/
│           ├── mapper/                # MyBatis XML Mapper
│           └── application.properties # 应用配置
├── frontend/                          # Vue 3 前端
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── api/request.js             # Axios 请求封装
│       ├── assets/                    # 样式与静态资源
│       │   ├── styles/
│       │   └── vendor/
│       ├── components/AppShell.vue    # 全局布局（侧边栏 + 顶栏）
│       ├── legacy/                    # 旧版页面迁移兼容脚本
│       │   ├── common-globals.js
│       │   ├── pageLoader.js
│       │   └── pages/                 # 旧版页面（15 个）
│       ├── router/index.js            # Vue Router 配置
│       ├── stores/auth.js             # Pinia 认证状态
│       └── views/                     # 新版页面视图（17 个）
├── sql/                               # 数据库完整建表脚本
│   ├── smart_contract_db_*.sql        # 各表结构 + 数据
│   └── migrate_*.sql                  # 增量迁移脚本
├── data/uploads/                      # 上传文件存储（按日期分目录）
├── .gitignore
├── README.md
└── PRODUCT.md
```

---

## 数据库表

共 **26 张表**：

| 表名 | 说明 | 核心字段 |
|------|------|----------|
| `contract_main` | 合同主表 | contract_id, title, content, status, amount, dept_id, creator_id |
| `contract_version` | 合同版本 | version_number, content_snapshot, html_content, file_path |
| `contract_template` | 合同模板 | template_name, content, fields_definition |
| `contract_attachment` | 合同附件 | file_name, file_type, file_size, storage_path, ocr_status |
| `contract_attachment_ocr` | OCR 识别结果 | ocr_text, layout_html, confidence |
| `approval_instance` | 审批实例 | contract_id, approval_type, status |
| `approval_record` | 审批记录 | instance_id, approver_id, action, comment |
| `seal_record` | 签章记录 | contract_id, seal_type, seal_date |
| `archive_record` | 归档记录 | contract_id, archive_number, archived_at |
| `blockchain_record` | 区块链存证 | contract_hash, transaction_id, block_number |
| `fulfillment_plan` | 履约计划 | contract_id, plan_date, deliverables |
| `fulfillment_deliverable` | 交付物 | plan_id, deliverable_name, status, confirmed_at |
| `fulfillment_delay_approval` | 延期审批 | deliverable_id, delay_days, approved |
| `fulfillment_progress_log` | 履约进度日志 | contract_id, progress_percent, log_date |
| `fulfillment_voucher` | 履约凭证 | deliverable_id, voucher_type, file_path |
| `fulfillment_overdue_history` | 逾期历史 | contract_id, overdue_days, calculated_at |
| `payment_plan` | 付款计划 | contract_id, amount, due_date |
| `payment_record` | 付款记录 | plan_id, paid_amount, paid_date |
| `invoice_record` | 发票记录 | payment_id, invoice_number, invoice_date |
| `reminder_record` | 提醒记录 | contract_id, remind_type, remind_date |
| `risk_report` | 风险报告 | contract_id, report_content, risk_level |
| `risk_item` | 风险条目 | report_id, risk_type, severity, description |
| `ai_task_record` | AI 任务记录 | task_type, prompt, result, tokens_used |
| `operation_log` | 操作审计日志 | user_id, action, target, ip_address |
| `integration_log` | 外部集成日志 | integration_type, request, response |
| `user_info` | 用户表 | username, password_hash, role, dept_id |
| `dept_info` | 部门表 | dept_name, parent_id |
| `role_info` | 角色表 | role_code, role_name |
| `file_info` | 文件信息 | file_name, storage_path, file_size, md5 |

---

## 快速开始

### 环境要求

| 依赖 | 最低版本 | 说明 |
|------|----------|------|
| JDK | 17 | 后端运行 |
| Maven | 3.9+ | 后端构建 |
| Node.js | 20+ | 前端运行 |
| MySQL | 8.0 | 主数据库 |
| Redis | 7.0+ | 缓存（可选，系统可降级运行） |

### 1. 创建数据库

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS smart_contract_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

然后导入 `sql/` 目录下的建表脚本：

```bash
# 导入所有表的建表 + 初始数据
mysql -u root -p smart_contract_db < sql/smart_contract_db_user_info.sql
mysql -u root -p smart_contract_db < sql/smart_contract_db_dept_info.sql
# ... 依次导入其余表
```

### 2. 配置环境变量

```bash
# 通义千问 API Key（必填，用于 AI 起草和风险审查）
export DASHSCOPE_API_KEY="your-dashscope-api-key"

# PaddleOCR Token（使用 PaddleOCR 时必填）
export PADDLE_OCR_TOKEN="your-paddleocr-token"
```

### 3. 修改数据库密码

编辑 `backend/src/main/resources/application.properties`：

```properties
spring.datasource.username=root
spring.datasource.password=你的MySQL密码
```

### 4. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端运行在 `http://localhost:8080`。

### 5. 启动前端

```bash
cd frontend
npm install        # 首次运行
npm run dev
```

前端运行在 `http://localhost:5173`，Vite 自动将 `/api` 请求代理至后端 `8080` 端口。

### 6. 登录

打开 `http://localhost:5173`，默认管理员账号：

```
用户名：admin
密码：@123
```

| 演示账号 | 角色 | 密码 |
|----------|------|------|
| `admin` | 系统管理员 | `@123` |
| `legal` | 法务专员 | `@123` |
| `finance` | 财务专员 | `@123` |
| `executive` | 企业高管 | `@123` |
| `dept_leader` | 部门主管 | `@123` |
| `user` | 普通员工 | `@123` |

---

## 配置说明

> 敏感凭据请通过环境变量注入，不要将生产密钥提交到仓库。

### 数据库

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/smart_contract_db?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=your-password
```

### AI（通义千问 · 阿里云百炼）

```properties
ai.qwen.enabled=true
ai.qwen.api-key=${DASHSCOPE_API_KEY}
ai.qwen.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1
ai.qwen.model=qwen-plus
ai.qwen.timeout-seconds=120
ai.qwen.vision-model=qwen-vl-plus
ai.qwen.enable-thinking=false
```

### OCR（PaddleOCR 云服务）

```properties
ai.ocr.enabled=true
ai.ocr.provider=paddle                    # paddle | aliyun-openapi | aliyun
ai.ocr.paddle-token=${PADDLE_OCR_TOKEN}
ai.ocr.paddle-model=PaddleOCR-VL-1.6
ai.ocr.use-doc-orientation-classify=true   # 文档方向校正
ai.ocr.use-doc-unwarping=true             # 文档展平
ai.ocr.use-chart-recognition=true         # 图表识别
```

### 电子签章（法大大 FASC v5）

```properties
signature.provider=fadada
signature.fadada-api-url=https://uat-api.fadada.com/api/v5
signature.fadada-app-id=your-app-id
signature.fadada-app-secret=your-app-secret
signature.fadada-callback-url=https://your-domain/api/signature/callback
```

### 文件存储

```properties
storage.local.base-dir=../data/uploads
spring.servlet.multipart.max-file-size=200MB
spring.servlet.multipart.max-request-size=210MB
```

### 审批阈值

```properties
contract.threshold.major=100000    # 重大合同最低金额（元），触发 Legal + Executive 会签
contract.threshold.super=500000    # 超阈值合同最低金额（元），触发同步会签
```

---

## 前端页面

系统采用 Vue Router 单页应用，侧边栏和顶栏由 `AppShell.vue` 统一承载。

| 页面 | 路由 | 视图组件 | 说明 |
|------|------|----------|------|
| 登录 | `/login` | `Login.vue` | 用户登录 |
| 注册 | `/register` | `Register.vue` | 用户注册 |
| 工作台 | `/dashboard` | `Dashboard.vue` | 合同指标、趋势图表、到期预警、待办清单 |
| 合同编制 | `/draft` | `Draft.vue` | 合同草稿 CRUD、筛选、批量操作 |
| 在线编辑 | `/edit` | `Edit.vue` | 富文本编辑、OCR 导入、模板填充、DOCX 导出 |
| 合同模板 | `/templates` | `Templates.vue` | 模板浏览、使用与管理 |
| 风险审查 | `/risk` | `Risk.vue` | AI 风险审查触发、报告查看、DOCX 导出 |
| 审批中心 | `/approval` | `Approval.vue` | 审批流转、同意/驳回、审批历史 |
| 合同台账 | `/ledger` | `Ledger.vue` | 全量合同台账、状态筛选、详情查看 |
| 签章登记 | `/seal` | `Seal.vue` | 电子签章登记与用印记录 |
| 归档确认 | `/archive` | `Archive.vue` | 归档编号生成、版本锁定 |
| 区块链存证 | `/blockchain` | `Blockchain.vue` | 合同哈希上链、存证记录查询 |
| 电子签章 | `/signature` | `Signature.vue` | 法大大电子签章发起与管理 |
| 履约预警 | `/fulfillment` | `Fulfillment.vue` | 履约计划、交付物确认、付款台账、逾期预警 |
| 用户管理 | `/users` | `Users.vue` | 用户与角色管理（ADMIN） |
| 审计日志 | `/audit` | `Audit.vue` | 操作审计与安全事件查询（ADMIN） |

旧版页面保留在 `frontend/src/legacy/pages/` 目录，路由通过 `/html/xxx.html` 和 `/xxx.html` 重定向至新版 SPA 页面。

---

## 后端模块

### Controller 层（25 个）

| Controller | 路径前缀 | 职责 |
|------------|----------|------|
| `ContractController` | `/api/contracts` | 合同 CRUD、AI 起草（SSE）、提交审批、字段分析 |
| `ApprovalController` | `/api/approvals` | 审批列表、同意、驳回 |
| `AttachmentController` | `/api/attachments` | 附件上传、下载、关联、OCR 触发、模板分析 |
| `TemplateController` | `/api/templates` | 模板 CRUD 与启停 |
| `VersionController` | `/api/contracts/{id}/versions` | 版本保存、查询、恢复、下载 |
| `RiskController` | `/api/risk-reports`, `/api/risks` | 风险审查触发、报告列表、详情、导出 |
| `AiSecurityController` | `/api/ai` | AI 安全审查与合规分析 |
| `SealController` | `/api/contracts/{id}/seal` | 签章登记与记录查询 |
| `ArchiveController` | `/api/contracts/{id}/archive` | 归档确认与记录查询 |
| `BlockchainController` | `/api/blockchain` | 区块链上链存证 |
| `SignatureController` | `/api/signature` | 法大大电子签章发起与回调 |
| `FulfillmentController` | `/api/fulfillment-plans` | 履约计划管理 |
| `PerformanceController` | `/api/performance` | 交付物、付款、逾期、进度统计 |
| `DashboardController` | `/api/dashboard` | 工作台汇总数据 |
| `LedgerController` | `/api/ledger` | 合同台账与状态筛选 |
| `ContractImportController` | `/api/contracts/import` | 批量合同导入 |
| `MarkdownController` | `/api/contracts/*/export/markdown` | Markdown 解析、导入、导出、预览 |
| `UserController` | `/api/users` | 登录、注册、登出、当前用户信息 |
| `AdminController` | `/api/admin` | 用户管理、角色管理（ADMIN） |
| `CounterpartyController` | `/api/counterparties` | 交易对手方管理 |
| `DeptController` | `/api/departments` | 部门管理 |
| `MenuController` | `/api/menus` | 侧边栏菜单配置 |
| `SecurityAuditController` | `/api/security-audit` | 审计日志查询 |
| `SecurityEventController` | `/api/security-events` | 安全事件采集与查询 |
| `GlobalExceptionHandler` | — | 全局异常拦截与统一错误响应 |

### Service 层（34 个）

| Service | 职责 |
|---------|------|
| `ContractManagementService` | 合同 CRUD、数据权限过滤、审批提交 |
| `AiDraftService` | AI 起草合同正文（SSE 流式） |
| `QwenContractService` | 通义千问通用调用封装 |
| `ComplianceAiService` | AI 合规性审查 |
| `ContractFieldAnalysisService` | 合同正文字段识别与建议值填充 |
| `ApprovalService` | 多级审批流程处理与状态流转 |
| `ContractVersionService` | 版本快照、恢复、HTML 清洗、归档文件 |
| `ContractAttachmentService` | 附件上传、关联、OCR 触发、下载 |
| `OcrService` | PaddleOCR / 阿里云 OCR 统一调用 |
| `OcrEditorHtmlService` | OCR 版式 HTML 生成 |
| `DocumentParseService` | PDF / DOCX / DOC 文本提取 |
| `ContractDocumentImportService` | 文档导入合同 |
| `TemplateService` | 模板 CRUD |
| `ContractImportService` | 批量合同导入 |
| `RiskReportExportService` | 风险报告 DOCX 导出 |
| `FulfillmentService` | 履约计划管理 |
| `FulfillmentArchiveListener` | 归档后自动创建履约计划 |
| `StatusTransitionService` | 签章、归档及合同状态机流转 |
| `BlockchainService` | 区块链存证与查询 |
| `SignatureService` | 电子签章服务编排 |
| `FadadaSignatureProvider` | 法大大 FASC v5 API 对接 |
| `LocalSignatureProvider` | 本地签章（开发/降级用） |
| `MenuService` | 菜单配置管理 |
| `AuthService` | 登录认证 |
| `TokenService` | JWT 签发与校验 |
| `SecurityAuditService` | 审计日志记录与查询 |
| `SecurityEventService` | 安全事件采集与存储 |
| `ContractNumberService` | 合同编号自动生成 |
| `MarkdownContractService` | Markdown 导入导出 |
| `WordArchiveService` | HTML 转 DOCX 导出 |
| `FileStorageService` | 本地文件存储（按日期分目录） |
| `CounterpartyService` | 交易对手方管理 |
| `DeptService` | 部门管理 |
| `ExternalIntegrationProperties` | OA / HR / 签章外部集成配置 |

---

## API 参考

除登录、注册接口外，所有 `/api/**` 请求均需携带 JWT：

```http
Authorization: Bearer <accessToken>
```

### 认证与用户

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/users/login` | 登录 | 公开 |
| POST | `/api/users/register` | 注册 | 公开 |
| POST | `/api/users/logout` | 登出 | 登录用户 |
| GET | `/api/users/current` | 当前用户信息 | 登录用户 |
| GET | `/api/admin/users` | 用户列表 | ADMIN |
| PUT | `/api/admin/users/{userId}/role` | 修改角色 | ADMIN |
| GET | `/api/admin/roles` | 角色列表 | ADMIN |
| GET | `/api/admin/audit-logs` | 审计日志 | ADMIN |

### 合同与工作台

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/dashboard` | 工作台汇总 |
| GET | `/api/contracts` | 合同列表（含筛选） |
| POST | `/api/contracts` | 创建合同 |
| PUT | `/api/contracts/{contractId}` | 修改合同 |
| DELETE | `/api/contracts/{contractId}` | 删除草稿 |
| POST | `/api/contracts/{contractId}/submit` | 提交审批 |
| GET | `/api/counterparties` | 对手方列表 |
| GET | `/api/departments` | 部门列表 |

### AI 起草与审查

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/contracts/field-analysis` | 识别合同正文待填字段 | 登录用户 |
| POST | `/api/ai/risk-review` | 执行 AI 风险审查 | LEGAL / EXECUTIVE / ADMIN |
| POST | `/api/ai/compliance-review` | AI 合规审查 | LEGAL / EXECUTIVE / ADMIN |
| GET | `/api/risk-reports` | 风险报告列表 | DEPT_LEADER+ |
| GET | `/api/risk-reports/{reportId}` | 报告详情 | DEPT_LEADER+ |
| GET | `/api/risk-reports/{reportId}/export` | 导出 DOCX | LEGAL / EXECUTIVE / ADMIN |
| GET | `/api/risks` | 风险项查询 | DEPT_LEADER+ |

### 附件与 OCR

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/attachments/upload` | 上传附件 |
| GET | `/api/attachments` | 附件列表 |
| GET | `/api/attachments/{id}` | 附件详情 |
| POST | `/api/attachments/{id}/ocr` | 触发 OCR |
| GET/POST | `/api/attachments/{id}/draft-analysis` | 模板字段分析 |
| POST | `/api/attachments/{id}/link` | 关联合同 |
| POST | `/api/contracts/from-ocr` | 从 OCR 结果创建合同 |
| GET | `/api/attachments/{id}/download` | 下载附件 |

### 审批、签章与归档

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/approvals` | 审批列表 |
| POST | `/api/approvals/{instanceId}/agree` | 同意 |
| POST | `/api/approvals/{instanceId}/reject` | 驳回 |
| POST | `/api/contracts/{contractId}/seal` | 签章登记 |
| POST | `/api/contracts/{contractId}/archive` | 归档确认 |
| GET | `/api/contracts/{contractId}/seal-records` | 签章记录 |
| GET | `/api/contracts/{contractId}/archive-records` | 归档记录 |

### 电子签章

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/signature/apply` | 发起电子签章 |
| GET | `/api/signature/status/{contractId}` | 查询签章状态 |
| POST | `/api/signature/callback` | 法大大回调（公网） |

### 区块链存证

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/blockchain/record` | 合同上链存证 |
| GET | `/api/blockchain/records/{contractId}` | 存证记录查询 |

### 版本、模板与 Markdown

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/contracts/{id}/versions` | 保存版本 |
| GET | `/api/contracts/{id}/versions` | 版本列表 |
| GET | `/api/contracts/{id}/versions/latest` | 最新版本 |
| GET | `/api/contracts/{id}/versions/{versionId}` | 版本详情 |
| POST | `/api/contracts/{id}/versions/{versionId}/restore` | 恢复版本 |
| GET | `/api/contracts/{id}/versions/{versionId}/download` | 下载版本 |
| GET/POST/PUT/DELETE | `/api/templates/**` | 模板 CRUD |
| GET | `/api/contracts/{id}/export/markdown` | 导出 Markdown |
| POST | `/api/contracts/import/markdown` | 导入 Markdown |

### 履约与绩效

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/fulfillment-plans` | 履约计划列表 |
| PUT | `/api/fulfillment-plans/{planId}/status` | 更新履约状态 |
| POST | `/api/performance/deliverables` | 新增交付物 |
| GET | `/api/performance/deliverables` | 交付物列表 |
| PUT | `/api/performance/deliverables/{id}/confirm` | 确认交付 |
| PUT | `/api/performance/deliverables/{id}/unconfirm` | 取消确认 |
| POST | `/api/performance/payment-plans` | 新增付款计划 |
| GET | `/api/performance/payment-plans` | 付款计划列表 |
| POST | `/api/performance/payment-records` | 新增付款记录 |
| GET | `/api/performance/payment-records` | 付款记录列表 |
| GET | `/api/performance/overdue-list` | 逾期清单 |
| GET | `/api/performance/progress/{contractId}` | 履约进度 |

### 安全审计

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/security-audit/logs` | 操作审计日志 | ADMIN |
| GET | `/api/security-events` | 安全事件列表 | ADMIN |

---

## 安全模型

### 角色体系

| 角色 | 编码 | 数据范围 | 典型权限 |
|------|------|----------|----------|
| 系统管理员 | `ADMIN` | ALL | 用户管理、角色变更、审计日志、系统配置 |
| 法务专员 | `LEGAL` | ALL | 风险审查、合规分析、报告导出、审批 |
| 财务专员 | `FINANCE` | ALL | 付款审批、发票管理、财务台账 |
| 企业高管 | `EXECUTIVE` | ALL | 重大/超阈值合同审批、全局视图 |
| 部门主管 | `DEPT_LEADER` | DEPT | 部门合同管理与审批、履约监督 |
| 普通员工 | `USER` | SELF | 个人合同起草、查看、编辑 |

### 数据权限

- **SELF** — 只能访问本人负责的合同
- **DEPT** — 可访问本部门所有合同
- **ALL** — 可访问全部合同

### 审计追踪

- `@AuditOperation` 注解自动记录操作至 `operation_log` 表
- `SecurityEventService` 采集安全事件（登录失败、权限不足等）
- 审计写操作失败不阻塞原业务请求
- 页面登录用户水印防截屏泄露

### 数据脱敏

`SensitiveDataMasker` 对日志和审计中的敏感字段（密码、JWT、API Key、合同正文）自动脱敏。

---

## Git 提交说明

### 应提交

```
SmartContract/
├── .gitignore
├── README.md
├── PRODUCT.md
├── backend/                       # 整个后端源码
│   ├── pom.xml
│   └── src/                       # （target/ 已在 .gitignore 排除）
├── frontend/                      # 整个前端源码
│   ├── package.json
│   ├── package-lock.json
│   ├── vite.config.js
│   ├── index.html
│   ├── src/
│   └── public/                    # （node_modules/ dist/ 已在 .gitignore 排除）
├── sql/                           # 数据库脚本
│   └── *.sql
├── data/uploads/                  # 上传的合同文件（按日期分目录）
└── .agents/                       # Claude Code 技能定义（可选）
```

### 不应提交（已在 .gitignore）

| 路径 | 原因 |
|------|------|
| `backend/target/` | Maven 构建产物 |
| `frontend/node_modules/` | npm 依赖 |
| `frontend/dist/` | Vite 构建产物 |
| `.idea/` | IntelliJ IDEA 个人配置 |
| `*.log`、`*.pid` | 运行日志和进程文件 |

### .gitignore 建议补充

当前 `.gitignore` 已排除 `data/uploads/`，如果你需要提交合同文件，需要删除这行：

```diff
- data/uploads/
+ # data/uploads/  # 取消忽略，提交合同文件
```

日志和 PID 文件建议追加：

```gitignore
# Runtime logs & PID
*.log
*.pid
```
