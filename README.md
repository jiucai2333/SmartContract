<div align="center">

# SmartContract

### 基于大模型的中小企业合同全生命周期管理系统

<p>
  <img alt="Java" src="https://img.shields.io/badge/Java-17-orange?style=flat-square" />
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen?style=flat-square" />
  <img alt="Vue" src="https://img.shields.io/badge/Vue-3-42b883?style=flat-square" />
  <img alt="MySQL" src="https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat-square" />
  <img alt="Redis" src="https://img.shields.io/badge/Redis-7.0+-DC382D?style=flat-square" />
  <img alt="AI" src="https://img.shields.io/badge/AI-%E9%80%9A%E4%B9%89%E5%8D%83%E9%97%AE-blueviolet?style=flat-square" />
</p>

<p>
  <b>合同起草</b> · <b>AI 风险审查</b> · <b>审批流转</b> · <b>电子签章</b> · <b>归档存证</b> · <b>履约预警</b> · <b>权限审计</b>
</p>

</div>

---

## 项目简介

**SmartContract** 是一个面向中小企业场景的合同编制审批管理系统。系统以合同主数据为中心，将合同从草拟、风险审查、审批、签章、归档到履约完成的全过程进行统一管理，并结合大语言模型、OCR、电子签章和审计日志能力，提升合同处理效率、流程规范性和业务可追溯性。

系统覆盖合同全生命周期：

```text
起草 → AI 辅助 → 审批 → 签章 → 归档 → 履约追踪 → 完成
```

对应状态流转如下：

```text
DRAFT → APPROVING → APPROVED → SIGNING → ARCHIVED → EXECUTING → COMPLETED
                                                         ↘ EXPIRED / TERMINATED
```

---

## 目录

- [项目简介](#项目简介)
- [核心亮点](#核心亮点)
- [功能模块](#功能模块)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [前端页面](#前端页面)
- [后端模块](#后端模块)
- [数据库说明](#数据库说明)
- [API 参考](#api-参考)
- [安全模型](#安全模型)
- [Git 提交说明](#git-提交说明)

---

## 核心亮点

| 亮点 | 说明 |
|---|---|
| 全生命周期闭环 | 覆盖合同起草、审批、签章、归档、履约、完成等关键阶段 |
| AI 合同能力 | 支持 AI 起草、字段识别、风险审查和合规分析 |
| OCR 文档识别 | 支持合同附件识别、版式还原、表格识别和方向校正 |
| 审批规则分级 | 根据合同金额和角色权限触发不同审批路径 |
| 电子签章集成 | 对接法大大 FASC v5 API，同时保留本地签章降级方案 |
| 履约预警管理 | 支持交付物、付款计划、付款记录、逾期提醒与进度跟踪 |
| 权限与审计 | 基于 JWT、角色权限、数据范围和操作审计实现安全控制 |

---

## 功能模块

### 合同生命周期管理

| 阶段 | 主要功能 | 说明 |
|---|---|---|
| 起草 | 在线编辑器、模板填充、AI 起草 | 支持富文本、Markdown、DOCX 导入导出 |
| 审查 | AI 风险审查、合规分析 | 生成风险等级、风险条目和审查报告 |
| 审批 | 多级审批、同意、驳回 | 支持普通合同、重大合同、超阈值合同审批 |
| 签章 | 电子签章登记、签章状态同步 | 对接法大大签章服务，可本地降级 |
| 归档 | 归档编号、版本锁定、存证 | 归档后禁止编辑，保留正式版本 |
| 履约 | 交付物、付款台账、逾期预警 | 追踪履约进度和付款执行情况 |
| 审计 | 操作日志、安全事件 | 支持权限不足、登录失败等安全事件记录 |

### AI 能力

| 能力 | 模型/服务 | 说明 |
|---|---|---|
| AI 起草 | 通义千问 `qwen-plus` | 基于合同类型、相对方、金额等信息生成合同正文 |
| 字段识别 | 通义千问 | 从合同正文中提取可填充字段和建议值 |
| 风险审查 | 通义千问 | 识别合同条款风险并给出等级评定 |
| 合规审查 | 通义千问 | 分析合同文本是否存在合规问题 |
| OCR 识别 | PaddleOCR-VL-1.6 | 支持文档方向校正、版式还原、表格识别 |

### 文档处理能力

- 支持 `PDF`、`DOCX`、`DOC`、`Markdown`、`HTML` 等格式。
- 使用 Apache POI、PDFBox、Jsoup 进行文档解析和内容清洗。
- 支持合同 DOCX 导出、风险报告导出、Markdown 导入导出。
- 支持上传附件、OCR 识别、模板字段分析和合同正文回填。

---

## 技术栈

| 分类 | 技术 | 版本/说明 |
|---|---|---|
| 后端语言 | Java | 17 |
| 后端框架 | Spring Boot | 3.5.x |
| Web 框架 | Spring MVC + Jakarta Validation | RESTful API、参数校验 |
| ORM | MyBatis-Plus | 3.5.x |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis / Spring Data Redis | 可选，系统可降级运行 |
| 认证 | JWT + BCrypt | Token 登录、密码哈希 |
| AI 服务 | 通义千问 | OpenAI 兼容接口，`qwen-plus` |
| OCR 服务 | PaddleOCR 云服务 | PaddleOCR-VL-1.6 |
| 文档处理 | Apache POI / PDFBox / Jsoup | DOCX、PDF、HTML 解析 |
| 电子签章 | 法大大 FASC v5 API | 签章发起、状态同步、回调处理 |
| 前端框架 | Vue 3 + Vite | 单页应用开发 |
| 前端路由 | Vue Router | 页面路由管理 |
| 状态管理 | Pinia | 登录态与用户信息管理 |
| 构建工具 | Maven + Vite | 后端与前端分别构建 |

---

## 项目结构

```text
SmartContract/
├── backend/                           # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/cupk/smartcontract/
│       │   ├── SmartContractApplication.java
│       │   ├── common/                # 通用响应、枚举、常量
│       │   ├── config/                # MyBatis、AI、OCR、存储、签章配置
│       │   ├── controller/            # REST Controller
│       │   ├── dto/                   # 请求与响应 DTO
│       │   ├── entity/                # 数据库实体
│       │   ├── mapper/                # MyBatis-Plus Mapper
│       │   ├── security/              # JWT、角色控制、审计、脱敏
│       │   └── service/               # 核心业务服务
│       │       └── signature/         # 签章服务适配层
│       └── resources/
│           ├── mapper/                # XML Mapper
│           └── application.properties # 应用配置
├── frontend/                          # Vue 3 前端
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── api/                       # Axios 请求封装
│       ├── assets/                    # 样式与静态资源
│       ├── components/                # 公共组件
│       ├── legacy/                    # 旧版页面兼容脚本
│       ├── router/                    # Vue Router 配置
│       ├── stores/                    # Pinia 状态管理
│       └── views/                     # 页面视图
├── sql/                               # 建表脚本与迁移脚本
├── data/uploads/                      # 本地上传文件存储目录
├── .gitignore
├── README.md
└── PRODUCT.md
```

---

## 快速开始

### 环境要求

| 依赖 | 建议版本 | 用途 |
|---|---|---|
| JDK | 17+ | 后端运行 |
| Maven | 3.9+ | 后端构建 |
| Node.js | 20+ | 前端运行 |
| MySQL | 8.0+ | 主数据库 |
| Redis | 7.0+ | 缓存，可选 |

### 1. 创建数据库

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS smart_contract_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

导入 `sql/` 目录下的建表脚本：

```bash
mysql -u root -p smart_contract_db < sql/smart_contract_db_user_info.sql
mysql -u root -p smart_contract_db < sql/smart_contract_db_dept_info.sql
# 按依赖顺序继续导入其余表脚本
```

> 如果项目中已经提供完整建库脚本，建议优先使用完整脚本一次性导入，避免表依赖顺序问题。

### 2. 配置环境变量

```bash
# 通义千问 API Key：用于 AI 起草、字段识别和风险审查
export DASHSCOPE_API_KEY="your-dashscope-api-key"

# PaddleOCR Token：使用 PaddleOCR 云服务时需要配置
export PADDLE_OCR_TOKEN="your-paddleocr-token"
```

Windows PowerShell 可使用：

```powershell
$env:DASHSCOPE_API_KEY="your-dashscope-api-key"
$env:PADDLE_OCR_TOKEN="your-paddleocr-token"
```

### 3. 修改数据库连接

编辑 `backend/src/main/resources/application.properties`：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/smart_contract_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
spring.datasource.username=root
spring.datasource.password=your-password
```

### 4. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端默认运行地址：

```text
http://localhost:8080
```

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认运行地址：

```text
http://localhost:5173
```

Vite 会将 `/api` 请求代理至后端 `8080` 端口。

### 6. 登录系统

打开浏览器访问：

```text
http://localhost:5173
```

默认演示账号如下：

| 账号 | 角色 | 密码 |
|---|---|---|
| `admin` | 系统管理员 | `@123` |
| `legal` | 法务专员 | `@123` |
| `finance` | 财务专员 | `@123` |
| `executive` | 企业高管 | `@123` |
| `dept_leader` | 部门主管 | `@123` |
| `user` | 普通员工 | `@123` |

> 演示账号仅用于本地开发与课程展示。部署到正式环境前，应修改默认密码并关闭不必要的测试账号。

---

## 配置说明

### 数据库配置

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/smart_contract_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
spring.datasource.username=root
spring.datasource.password=your-password
```

### AI 配置

```properties
ai.qwen.enabled=true
ai.qwen.api-key=${DASHSCOPE_API_KEY}
ai.qwen.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1
ai.qwen.model=qwen-plus
ai.qwen.timeout-seconds=120
ai.qwen.vision-model=qwen-vl-plus
ai.qwen.enable-thinking=false
```

### OCR 配置

```properties
ai.ocr.enabled=true
ai.ocr.provider=paddle
ai.ocr.paddle-token=${PADDLE_OCR_TOKEN}
ai.ocr.paddle-model=PaddleOCR-VL-1.6
ai.ocr.use-doc-orientation-classify=true
ai.ocr.use-doc-unwarping=true
ai.ocr.use-chart-recognition=true
```

### 电子签章配置

```properties
signature.provider=fadada
signature.fadada-api-url=https://uat-api.fadada.com/api/v5
signature.fadada-app-id=your-app-id
signature.fadada-app-secret=your-app-secret
signature.fadada-callback-url=https://your-domain/api/signature/callback
```

### 文件存储配置

```properties
storage.local.base-dir=../data/uploads
spring.servlet.multipart.max-file-size=200MB
spring.servlet.multipart.max-request-size=210MB
```

### 审批阈值配置

```properties
contract.threshold.major=100000
contract.threshold.super=500000
```

| 配置项 | 含义 |
|---|---|
| `contract.threshold.major` | 重大合同金额阈值，达到后触发更高层级审批 |
| `contract.threshold.super` | 超阈值合同金额阈值，达到后触发更严格审批路径 |

---

## 前端页面

系统采用 Vue Router 单页应用，由 `AppShell.vue` 统一承载侧边栏和顶栏。

| 页面 | 路由 | 组件 | 说明 |
|---|---|---|---|
| 登录 | `/login` | `Login.vue` | 用户登录 |
| 注册 | `/register` | `Register.vue` | 用户注册 |
| 工作台 | `/dashboard` | `Dashboard.vue` | 指标统计、趋势图表、待办和预警 |
| 合同编制 | `/draft` | `Draft.vue` | 草稿管理、筛选、批量操作 |
| 在线编辑 | `/edit` | `Edit.vue` | 富文本编辑、OCR 导入、模板填充、DOCX 导出 |
| 合同模板 | `/templates` | `Templates.vue` | 模板浏览、使用与管理 |
| 风险审查 | `/risk` | `Risk.vue` | AI 风险审查、报告查看和导出 |
| 审批中心 | `/approval` | `Approval.vue` | 审批处理与历史查询 |
| 合同台账 | `/ledger` | `Ledger.vue` | 全量合同台账与状态筛选 |
| 签章登记 | `/seal` | `Seal.vue` | 电子签章登记与用印记录 |
| 归档确认 | `/archive` | `Archive.vue` | 归档编号生成和版本锁定 |
| 区块链存证 | `/blockchain` | `Blockchain.vue` | 合同哈希上链和记录查询 |
| 电子签章 | `/signature` | `Signature.vue` | 法大大签章发起与管理 |
| 履约预警 | `/fulfillment` | `Fulfillment.vue` | 履约计划、交付物、付款和逾期预警 |
| 用户管理 | `/users` | `Users.vue` | 用户与角色管理 |
| 审计日志 | `/audit` | `Audit.vue` | 操作审计和安全事件查询 |

旧版页面保留在 `frontend/src/legacy/pages/`，用于历史页面迁移和兼容。

---

## 后端模块

### Controller 层

| Controller | 路径前缀 | 职责 |
|---|---|---|
| `ContractController` | `/api/contracts` | 合同 CRUD、AI 起草、提交审批、字段分析 |
| `ApprovalController` | `/api/approvals` | 审批列表、同意、驳回 |
| `AttachmentController` | `/api/attachments` | 附件上传、下载、OCR、模板分析 |
| `TemplateController` | `/api/templates` | 模板管理 |
| `VersionController` | `/api/contracts/{id}/versions` | 版本保存、恢复、下载 |
| `RiskController` | `/api/risk-reports`, `/api/risks` | 风险审查、报告查询、导出 |
| `AiSecurityController` | `/api/ai` | AI 安全审查与合规分析 |
| `SealController` | `/api/contracts/{id}/seal` | 签章登记 |
| `ArchiveController` | `/api/contracts/{id}/archive` | 归档确认 |
| `BlockchainController` | `/api/blockchain` | 区块链存证 |
| `SignatureController` | `/api/signature` | 法大大电子签章与回调 |
| `FulfillmentController` | `/api/fulfillment-plans` | 履约计划管理 |
| `PerformanceController` | `/api/performance` | 交付物、付款、逾期和进度统计 |
| `DashboardController` | `/api/dashboard` | 工作台汇总数据 |
| `LedgerController` | `/api/ledger` | 合同台账查询 |
| `UserController` | `/api/users` | 登录、注册、登出、当前用户信息 |
| `AdminController` | `/api/admin` | 用户与角色管理 |
| `SecurityAuditController` | `/api/security-audit` | 操作审计日志查询 |
| `SecurityEventController` | `/api/security-events` | 安全事件采集与查询 |
| `GlobalExceptionHandler` | — | 全局异常处理与统一响应 |

### Service 层

| Service | 职责 |
|---|---|
| `ContractManagementService` | 合同 CRUD、数据权限过滤、审批提交 |
| `AiDraftService` | AI 起草合同正文，支持 SSE 流式输出 |
| `QwenContractService` | 通义千问调用封装 |
| `ComplianceAiService` | AI 合规性审查 |
| `ContractFieldAnalysisService` | 合同字段识别和建议值填充 |
| `ApprovalService` | 多级审批流程和状态流转 |
| `ContractVersionService` | 版本快照、恢复、HTML 清洗、归档文件 |
| `ContractAttachmentService` | 附件上传、关联、OCR 触发、下载 |
| `OcrService` | OCR 统一调用适配 |
| `OcrEditorHtmlService` | OCR 版式 HTML 生成 |
| `DocumentParseService` | PDF、DOCX、DOC 文本提取 |
| `RiskReportExportService` | 风险报告 DOCX 导出 |
| `FulfillmentService` | 履约计划管理 |
| `FulfillmentArchiveListener` | 归档后自动创建履约计划 |
| `StatusTransitionService` | 签章、归档和合同状态机流转 |
| `BlockchainService` | 区块链存证与查询 |
| `SignatureService` | 电子签章编排 |
| `FadadaSignatureProvider` | 法大大签章服务对接 |
| `LocalSignatureProvider` | 本地签章降级方案 |
| `AuthService` | 登录认证 |
| `TokenService` | JWT 签发与校验 |
| `SecurityAuditService` | 审计日志记录和查询 |
| `SecurityEventService` | 安全事件采集和存储 |
| `ContractNumberService` | 合同编号生成 |
| `MarkdownContractService` | Markdown 导入导出 |
| `WordArchiveService` | HTML 转 DOCX 导出 |
| `FileStorageService` | 本地文件存储 |

---

## 数据库说明

系统数据围绕 `contract_main` 合同主表组织，并通过版本、附件、审批、签章、归档、风险、履约、付款、审计等表形成业务闭环。

> 为避免表数量与脚本版本不一致，README 中按业务模块展示核心表。实际表结构以 `sql/` 目录中的建库脚本为准。

| 模块 | 核心表 | 说明 |
|---|---|---|
| 合同主数据 | `contract_main` | 合同编号、名称、状态、金额、相对方、部门、创建人等 |
| 版本与模板 | `contract_version`, `contract_template` | 合同正文版本、模板内容和字段定义 |
| 附件与文件 | `contract_attachment`, `contract_attachment_ocr`, `file_info` | 上传文件、OCR 结果、统一文件元数据 |
| 审批流转 | `approval_instance`, `approval_record` | 审批实例、审批节点、审批意见和处理记录 |
| 签章归档 | `seal_record`, `archive_record`, `blockchain_record` | 签章记录、归档记录、区块链存证 |
| 风险审查 | `risk_report`, `risk_item`, `ai_task_record` | AI 风险报告、风险条目和 AI 调用记录 |
| 履约管理 | `fulfillment_plan`, `fulfillment_deliverable`, `fulfillment_progress_log` | 履约计划、交付物和进度日志 |
| 付款与发票 | `payment_plan`, `payment_record`, `invoice_record` | 付款计划、付款记录和发票信息 |
| 预警提醒 | `reminder_record`, `fulfillment_overdue_history` | 到期提醒、逾期记录和履约预警 |
| 权限基础 | `user_info`, `dept_info`, `role_info` | 用户、部门、角色和权限基础数据 |
| 审计集成 | `operation_log`, `integration_log` | 操作日志、外部系统调用日志 |

---

## API 参考

除登录、注册接口外，所有 `/api/**` 请求均需携带 JWT：

```http
Authorization: Bearer <accessToken>
```

### 认证与用户

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
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
|---|---|---|
| GET | `/api/dashboard` | 工作台汇总 |
| GET | `/api/contracts` | 合同列表，支持状态、关键字等筛选 |
| POST | `/api/contracts` | 创建合同 |
| PUT | `/api/contracts/{contractId}` | 修改合同 |
| DELETE | `/api/contracts/{contractId}` | 删除草稿 |
| POST | `/api/contracts/{contractId}/submit` | 提交审批 |
| GET | `/api/counterparties` | 对手方列表 |
| GET | `/api/departments` | 部门列表 |

### AI 起草与审查

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/api/contracts/field-analysis` | 识别合同正文待填字段 | 登录用户 |
| POST | `/api/ai/risk-review` | 执行 AI 风险审查 | LEGAL / EXECUTIVE / ADMIN |
| POST | `/api/ai/compliance-review` | AI 合规审查 | LEGAL / EXECUTIVE / ADMIN |
| GET | `/api/risk-reports` | 风险报告列表 | DEPT_LEADER+ |
| GET | `/api/risk-reports/{reportId}` | 报告详情 | DEPT_LEADER+ |
| GET | `/api/risk-reports/{reportId}/export` | 导出 DOCX | LEGAL / EXECUTIVE / ADMIN |
| GET | `/api/risks` | 风险项查询 | DEPT_LEADER+ |

### 附件与 OCR

| 方法 | 路径 | 说明 |
|---|---|---|
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
|---|---|---|
| GET | `/api/approvals` | 审批列表 |
| POST | `/api/approvals/{instanceId}/agree` | 审批同意 |
| POST | `/api/approvals/{instanceId}/reject` | 审批驳回 |
| POST | `/api/contracts/{contractId}/seal` | 签章登记 |
| POST | `/api/contracts/{contractId}/archive` | 归档确认 |
| GET | `/api/contracts/{contractId}/seal-records` | 签章记录 |
| GET | `/api/contracts/{contractId}/archive-records` | 归档记录 |

### 履约与绩效

| 方法 | 路径 | 说明 |
|---|---|---|
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

### 电子签章与存证

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/signature/apply` | 发起电子签章 |
| GET | `/api/signature/status/{contractId}` | 查询签章状态 |
| POST | `/api/signature/callback` | 法大大回调接口 |
| POST | `/api/blockchain/record` | 合同上链存证 |
| GET | `/api/blockchain/records/{contractId}` | 存证记录查询 |

### 版本、模板与 Markdown

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/contracts/{id}/versions` | 保存版本 |
| GET | `/api/contracts/{id}/versions` | 版本列表 |
| GET | `/api/contracts/{id}/versions/latest` | 最新版本 |
| GET | `/api/contracts/{id}/versions/{versionId}` | 版本详情 |
| POST | `/api/contracts/{id}/versions/{versionId}/restore` | 恢复版本 |
| GET | `/api/contracts/{id}/versions/{versionId}/download` | 下载版本 |
| GET/POST/PUT/DELETE | `/api/templates/**` | 模板 CRUD |
| GET | `/api/contracts/{id}/export/markdown` | 导出 Markdown |
| POST | `/api/contracts/import/markdown` | 导入 Markdown |

### 安全审计

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/security-audit/logs` | 操作审计日志 | ADMIN |
| GET | `/api/security-events` | 安全事件列表 | ADMIN |

---

## 安全模型

### 角色体系

| 角色 | 编码 | 数据范围 | 典型权限 |
|---|---|---|---|
| 系统管理员 | `ADMIN` | ALL | 用户管理、角色变更、审计日志、系统配置 |
| 法务专员 | `LEGAL` | ALL | 风险审查、合规分析、报告导出、审批 |
| 财务专员 | `FINANCE` | ALL | 付款审批、发票管理、财务台账 |
| 企业高管 | `EXECUTIVE` | ALL | 重大合同审批、超阈值合同审批、全局视图 |
| 部门主管 | `DEPT_LEADER` | DEPT | 部门合同管理、部门审批、履约监督 |
| 普通员工 | `USER` | SELF | 个人合同起草、查看和编辑 |

### 数据权限

| 数据范围 | 说明 |
|---|---|
| `SELF` | 只能访问本人负责或创建的合同 |
| `DEPT` | 可访问本部门合同 |
| `ALL` | 可访问全部合同 |

### 审计追踪

- 使用 `@AuditOperation` 自动记录关键业务操作。
- 审计数据写入 `operation_log` 表。
- `SecurityEventService` 采集登录失败、权限不足等安全事件。
- 审计写入失败不阻塞主业务流程。
- 页面支持登录用户水印，降低截图泄露风险。

### 数据脱敏

`SensitiveDataMasker` 会对日志和审计中的敏感字段进行脱敏，包括：

- 密码
- JWT Token
- API Key
- 合同正文
- 其他敏感业务字段

---

## Git 提交说明

建议提交信息采用以下格式：

```text
<type>(<scope>): <subject>
```

常用类型如下：

| 类型 | 说明 | 示例 |
|---|---|---|
| `feat` | 新功能 | `feat(contract): add contract draft editor` |
| `fix` | 修复问题 | `fix(auth): fix token expiration handling` |
| `docs` | 文档修改 | `docs(readme): optimize project documentation` |
| `style` | 代码格式调整 | `style(frontend): format vue pages` |
| `refactor` | 重构 | `refactor(service): simplify approval workflow` |
| `test` | 测试相关 | `test(contract): add draft service tests` |
| `chore` | 构建或杂项 | `chore: update dependencies` |

---

## 开发建议

- 后端接口建议统一使用 `/api` 前缀，便于前端代理、权限拦截和日志审计。
- 数据库脚本建议提供一个完整建库入口脚本，减少手动导入顺序错误。
- 默认账号和密钥只用于本地演示，部署前需要全部替换。
- AI、OCR、电子签章等外部服务建议保留降级策略，避免第三方服务异常影响核心业务。
- 履约数据应只绑定已签章、已归档、履约中或已完成等有效合同状态，避免草稿、审批中合同产生脏数据。

---

<div align="center">

**SmartContract · 让合同从起草到履约都有迹可循**

</div>
