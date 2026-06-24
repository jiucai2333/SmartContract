# SmartContract — 智能合同管理系统

基于 **Spring Boot 3.5** + **MyBatis-Plus** + **MySQL 8.0** + 原生 Web 技术构建的企业级合同全生命周期管理平台。

覆盖合同起草、模板管理、版本控制、附件 & OCR、AI 风险审查、审批流转、电子签章、归档确认、履约绩效和安全审计。

---

## 目录

- [功能总览](#功能总览)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [前端页面](#前端页面)
- [后端模块](#后端模块)
- [API 参考](#api-参考)
- [安全模型](#安全模型)
- [开发约定](#开发约定)

---

## 功能总览

### 合同全生命周期

```
DRAFT → APPROVING → APPROVED → SIGNING → ARCHIVED → EXECUTING → COMPLETED
                                                          ↘ EXPIRED / TERMINATED
```

- 合同草稿的创建、编辑、删除、筛选与台账查询
- 在线富文本编辑器 + 模板字段填充 + Word（DOCX）导出
- 版本快照、版本对比、版本恢复与历史文件下载
- 状态流转约束（如归档后禁止编辑、高风险合同拦截提交）
- 交易对手方与部门基础数据管理

### AI & 文档智能

| 能力 | 说明 |
|------|------|
| AI 合同起草 | 通义千问（Qwen）生成正文，支持 SSE 流式输出 |
| AI 模板字段识别 | 从合同正文自动提取可填充字段 |
| AI 风险审查 | 逐条风险识别、等级评定，报告落库 + DOCX 导出 |
| OCR 文档识别 | PaddleOCR / 阿里云 OCR，支持版式还原 |
| 文档解析 | PDF、DOCX、DOC 文本提取 |
| Markdown | 导入解析、预览、导出为 Markdown |

### 审批、签章与归档

- 三级审批：**普通** < **重大**（≥10 万，Legal + Executive 会签）< **超阈值**（≥50 万，同步会签）
- 审批同意 / 驳回、审批记录追溯
- 高风险合同在审批提交时自动阻断
- 电子签章登记与用印记录
- 归档编号自动生成、版本锁定

### 履约与绩效

- 履约计划制定与状态跟踪
- 交付物登记、确认 / 取消确认
- 付款计划与付款记录台账
- 逾期天数计算与逾期清单
- 合同履约进度统计
- 到期前 30 / 7 / 1 天分级提醒

### 安全与审计

- JWT（HMAC-SHA256）认证
- BCrypt 密码哈希
- `@RequireRole` 声明式角色控制
- SELF / DEPT / ALL 三级数据权限
- `@AuditOperation` 操作审计（失败不影响业务响应）
- 页面登录用户水印

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
| 认证 | 自签 JWT + Spring Security Crypto (BCrypt) | — |
| AI | 通义千问 Qwen（OpenAI 兼容接口） | — |
| OCR | PaddleOCR / 阿里云 OCR | — |
| 文档 | Apache POI / PDFBox | 5.4.1 / 3.0.5 |
| HTML 解析 | Jsoup | 1.20.1 |
| 工具 | Lombok | 1.18.46 |
| 前端 | HTML + CSS + 原生 JavaScript | — |
| 构建 | Maven + spring-boot-maven-plugin | — |

---

## 项目结构

```
SmartContract/
├── src/main/java/cupk/smartcontract/
│   ├── SmartContractApplication.java    # 启动入口
│   ├── common/                          # Result 响应体、RoleEnum 角色枚举
│   ├── config/                          # MVC、MyBatis-Plus、AI(Qwen)、OCR、存储配置
│   ├── controller/                      # 19 个 REST Controller
│   ├── dto/                             # 请求 / 响应 DTO（43 个）
│   ├── entity/                          # 数据库实体（24 个）
│   ├── mapper/                          # MyBatis-Plus Mapper（23 个）
│   ├── security/                        # JWT 拦截、@RequireRole、@AuditOperation 切面
│   └── service/                         # 业务 Service（26 个）
├── src/main/resources/
│   ├── static/
│   │   ├── css/                         # 全局样式 + 页面样式（13 个）
│   │   ├── html/                        # 前端页面（14 个）
│   │   └── js/                          # 全局脚本 + 页面脚本（13 个）
│   ├── sql/                             # 数据库增量脚本
│   └── application.properties           # 应用配置
├── data/uploads/                        # 上传文件存储目录
├── pom.xml
└── README.md
```

---

## 快速开始

### 环境要求

| 依赖 | 最低版本 |
|------|----------|
| JDK | 17 |
| Maven | 3.9+ |
| MySQL | 8.0 |
| Redis | 7.0+ |

### 1. 创建数据库

```sql
CREATE DATABASE smart_contract_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

然后执行 `src/main/resources/sql/` 下的增量脚本（按需执行）。

### 2. 配置环境变量

```powershell
# 通义千问 API Key（必填）
$env:DASHSCOPE_API_KEY = "你的 DashScope API Key"

# PaddleOCR Token（使用 PaddleOCR 时必填）
$env:PADDLE_OCR_TOKEN = "你的 PaddleOCR Token"
```

### 3. 启动

```powershell
# 开发模式
mvn spring-boot:run

# 打包部署
mvn package -DskipTests
java -jar target/smartcontract-0.0.1-SNAPSHOT.jar
```

### 4. 访问

浏览器打开 **http://localhost:8080/**，使用默认管理员账号登录：

```
用户名：admin
密码：Admin@123
```

---

## 配置说明

> 敏感凭据建议通过环境变量注入，不要将生产密钥提交到仓库。

### 数据库

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/smart_contract_db?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=你的密码
```

### AI（通义千问）

```properties
ai.qwen.enabled=true
ai.qwen.api-key=${DASHSCOPE_API_KEY}
ai.qwen.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1
ai.qwen.model=qwen-plus
ai.qwen.timeout-seconds=120
ai.qwen.vision-model=qwen-vl-plus
```

### OCR

```properties
ai.ocr.enabled=true
ai.ocr.provider=paddle          # paddle | aliyun-openapi | aliyun
ai.ocr.paddle-token=${PADDLE_OCR_TOKEN}
ai.ocr.paddle-model=PaddleOCR-VL-1.6
```

### 文件存储

```properties
storage.local.base-dir=./data/uploads
spring.servlet.multipart.max-file-size=200MB
spring.servlet.multipart.max-request-size=210MB
```

### 审批阈值

```properties
contract.threshold.major=100000    # 重大合同最低金额（元）
contract.threshold.super=500000    # 超阈值合同最低金额（元）
```

---

## 前端页面

系统采用多页面结构。侧边栏和顶栏由 `common.js` 统一渲染，公共样式集中在 `styles.css`。

| 页面 | 文件 | 功能说明 |
|------|------|----------|
| 入口 | [index.html](src/main/resources/static/html/index.html) | 根据登录状态跳转 |
| 登录 | [login.html](src/main/resources/static/html/login.html) | 用户登录 |
| 注册 | [register.html](src/main/resources/static/html/register.html) | 用户注册 |
| 工作台 | [dashboard.html](src/main/resources/static/html/dashboard.html) | 合同指标、趋势、到期预警、待办 |
| 合同编制 | [draft.html](src/main/resources/static/html/draft.html) | 合同草稿管理 |
| 在线编辑 | [edit.html](src/main/resources/static/html/edit.html) | 富文本编辑、OCR、模板、Word 下载 |
| 合同模板 | [templates.html](src/main/resources/static/html/templates.html) | 模板浏览与使用 |
| 风险审查 | [risk.html](src/main/resources/static/html/risk.html) | AI 风险审查与报告 |
| 审批中心 | [approval.html](src/main/resources/static/html/approval.html) | 审批流转与操作 |
| 合同台账 | [ledger.html](src/main/resources/static/html/ledger.html) | 合同台账与状态记录 |
| 签章登记 | [seal.html](src/main/resources/static/html/seal.html) | 电子签章登记 |
| 归档确认 | [archive.html](src/main/resources/static/html/archive.html) | 归档编号与版本锁定 |
| 履约预警 | [fulfillment.html](src/main/resources/static/html/fulfillment.html) | 履约计划、交付、付款、预警 |
| 用户管理 | [users.html](src/main/resources/static/html/users.html) | 用户与角色管理（ADMIN） |

---

## 后端模块

### Controller 层（19 个）

| Controller | 职责 |
|------|------|
| `ContractController` | 合同 CRUD、AI 起草 & SSE 流式、提交审批 |
| `RiskController` | 风险审查触发、报告列表、详情、导出、风险项查询 |
| `ApprovalController` | 审批列表、同意、驳回 |
| `AttachmentController` | 附件上传、下载、关联、OCR、模板字段分析 |
| `TemplateController` | 模板 CRUD 与启停 |
| `VersionController` | 版本保存、查询、恢复、下载 |
| `SealController` | 签章登记与记录查询 |
| `ArchiveController` | 归档确认与记录查询 |
| `FulfillmentController` | 履约计划管理 |
| `PerformanceController` | 交付物、付款、逾期、进度 |
| `MarkdownController` | Markdown 解析、导入、导出、预览 |
| `ContractImportController` | 批量合同导入 |
| `LedgerController` | 合同台账与状态 |
| `DashboardController` | 工作台汇总 |
| `UserController` | 登录、注册、登出、当前用户 |
| `AdminController` | 用户管理、角色管理、审计日志（ADMIN） |
| `CounterpartyController` | 交易对手方管理 |
| `DeptController` | 部门管理 |
| `SecurityAuditController` | 安全审计日志查询 |
| `GlobalExceptionHandler` | 全局异常处理 |

### Service 层（26 个）

| 服务 | 职责 |
|------|------|
| `ContractManagementService` | 合同 CRUD、数据权限、审批提交 |
| `AiDraftService` | AI 起草、风险分析、模板字段识别 |
| `ApprovalService` | 多级审批处理与状态流转 |
| `ContractVersionService` | 版本快照、恢复、HTML 清洗、归档文件 |
| `ContractAttachmentService` | 附件上传、关联、OCR 触发、下载 |
| `OcrService` | PaddleOCR / 阿里云 OCR 统一调用 |
| `RiskReportExportService` | 风险报告 DOCX 导出 |
| `FulfillmentService` | 履约计划管理 |
| `PerformanceService` | 交付物、付款、逾期、进度统计 |
| `StatusTransitionService` | 签章、归档及合同状态机流转 |
| `TemplateService` | 模板 CRUD |
| `DraftTemplateService` | 模板字段定义与解析 |
| `DocumentParseService` | PDF / DOCX / DOC 文本解析 |
| `ContractImportService` | 批量合同导入 |
| `ContractNumberService` | 合同编号生成 |
| `LayoutFeatureService` | OCR 版式特征提取 |
| `OcrLayoutHtmlService` | OCR 版式 HTML 生成 |
| `QwenOcrInputBuilder` | Qwen OCR 输入构建 |
| `MarkdownContractService` | Markdown 导入导出 |
| `WordArchiveService` | HTML 转 DOCX |
| `FileStorageService` | 本地文件存储（按日期分目录） |
| `AuthService` | 登录认证 |
| `TokenService` | JWT 签发与校验 |
| `SecurityAuditService` | 审计日志记录与查询 |
| `CounterpartyService` | 交易对手方 |
| `DeptService` | 部门管理 |

---

## API 参考

除登录、注册接口外，`/api/**` 路径均需携带 JWT：

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

### 合同 & 工作台

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

### AI 风险审查

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/ai/risk-review` | 执行 AI 风险审查 | LEGAL / EXECUTIVE / ADMIN |
| GET | `/api/risk-reports` | 报告列表 | DEPT_LEADER+ |
| GET | `/api/risk-reports/{reportId}` | 报告详情 | DEPT_LEADER+ |
| GET | `/api/risk-reports/{reportId}/export` | 导出 DOCX | LEGAL / EXECUTIVE / ADMIN |
| GET | `/api/risks` | 风险项查询（兼容） | DEPT_LEADER+ |

### AI 起草

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ai/draft` | AI 合同起草（同步） |
| POST | `/api/ai/draft-stream` | SSE 流式起草 |

### 附件 & OCR

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/attachments/upload` | 上传附件 |
| GET | `/api/attachments` | 附件列表 |
| GET | `/api/attachments/{id}` | 附件详情 |
| POST | `/api/attachments/{id}/ocr` | 触发 OCR |
| GET/POST | `/api/attachments/{id}/draft-analysis` | 模板字段分析 |
| POST | `/api/attachments/{id}/link` | 关联合同 |
| POST | `/api/contracts/from-ocr` | 从 OCR 创建合同 |
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

### 版本、模板与 Markdown

| 方法 | 路径 | 说明 |
|------|------|------|
| POST / GET | `/api/contracts/{contractId}/versions` | 保存 / 查询 |
| GET | `/api/contracts/{contractId}/versions/latest` | 最新版本 |
| GET | `/api/contracts/{contractId}/versions/{versionId}` | 版本详情 |
| POST | `/api/contracts/{contractId}/versions/{versionId}/restore` | 恢复版本 |
| GET | `/api/contracts/{contractId}/versions/{versionId}/download` | 下载版本文件 |
| GET/POST/PUT/DELETE | `/api/templates/**` | 模板 CRUD |
| GET | `/api/contracts/{contractId}/export/markdown` | 导出 Markdown |
| GET | `/api/contracts/{contractId}/export/markdown/preview` | Markdown 预览 |
| POST | `/api/contracts/import/markdown/parse` | 解析 Markdown |
| POST | `/api/contracts/import/markdown` | 导入 Markdown |

### 履约与绩效

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/fulfillment-plans` | 履约计划列表 |
| PUT | `/api/fulfillment-plans/{planId}/status` | 更新履约状态 |
| POST / GET | `/api/performance/deliverables` | 新增 / 查询交付物 |
| PUT | `/api/performance/deliverables/{id}/confirm` | 确认交付 |
| PUT | `/api/performance/deliverables/{id}/unconfirm` | 取消确认 |
| POST / GET | `/api/performance/payment-plans` | 付款计划 |
| POST / GET | `/api/performance/payment-records` | 付款记录 |
| GET | `/api/performance/overdue-list` | 逾期清单 |
| GET | `/api/performance/progress/{contractId}` | 履约进度 |

---

## 安全模型

### 角色体系

| 角色 | 编码 | 数据范围 | 典型权限 |
|------|------|----------|----------|
| 系统管理员 | `ADMIN` | ALL | 用户管理、角色变更、审计日志 |
| 法务专员 | `LEGAL` | ALL | 风险审查、报告导出、审批 |
| 财务专员 | `FINANCE` | ALL | 付款审批、财务台账 |
| 企业高管 | `EXECUTIVE` | ALL | 重大合同审批、全局视图 |
| 部门主管 | `DEPT_LEADER` | DEPT | 部门合同管理与审批 |
| 普通员工 | `USER` | SELF | 个人合同的起草与查看 |

### 数据权限

- **SELF** — 只能访问本人负责的合同
- **DEPT** — 可访问本部门所有合同
- **ALL** — 可访问全部合同

### 审计追踪

所有关键操作（登录、合同增删改、审批、签章、归档、风险报告）通过 `@AuditOperation` 注解记录到 `operation_log` 表。审计写操作失败不会阻塞原业务请求。

---

## 开发约定

- 前端侧边栏和顶栏统一使用 `common.js` 的 `appShellHtml()` 渲染，不在单页面重复
- 公共样式维护在 `styles.css`，页面 CSS 只处理页面主体区域
- 数据库结构变更使用独立增量 SQL 脚本，放置在 `src/main/resources/sql/`
- 日志中禁止输出密码、JWT、完整 AI Prompt、API Key 和敏感合同正文
- 所有新 API 接口必须配置 `@RequireRole`，关键写操作配置 `@AuditOperation`
- 查询合同关联数据时必须复用 `ContractManagementService` 中的数据权限逻辑
- 以下内容不提交到仓库：
  - `data/uploads/` 上传文件
  - 运行日志、IDE 输出目录
  - 真实密钥和凭据
