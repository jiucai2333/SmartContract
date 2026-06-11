# 智能合同管理系统

基于 Spring Boot 的企业级智能合同全生命周期管理平台，集成 AI 起草、OCR 识别、审批流程、风险审查、签章归档、履约跟踪等功能，支持 RBAC 六角色权限管控与行级数据隔离。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Java 17 + Spring Boot 3.5.14 |
| ORM | MyBatis-Plus 3.5.12（乐观锁 + 分页 + 逻辑删除） |
| 数据库 | MySQL 8.0 |
| 认证 | 自签 JWT (HMAC-SHA256) + BCrypt |
| AI 大模型 | 通义千问 Qwen（合同起草 / 风险审查 / 模板字段识别） |
| OCR | PaddleOCR / 阿里云 OCR（合同扫描件识别） |
| 文档解析 | Apache PDFBox 3.0.5 + Apache POI 5.4.1 |
| HTML 处理 | Jsoup 1.20.1（清洗 / Markdown 转换） |
| 前端 | 原生 HTML / CSS / JavaScript |

---

## 核心功能

### 合同全生命周期

```
草稿(DRAFT) → 审批中(APPROVING) → 已审批(APPROVED) → 已签章(SIGNING) → 已归档(ARCHIVED) → 履约中(EXECUTING) → 已完成(COMPLETED)
                                                                                                  ↘ 已到期(EXPIRED)
                                                                                                  ↘ 已终止(TERMINATED)
```

- **合同起草**：AI 辅助生成合同草稿（通义千问），支持 SSE 流式输出；从模板创建；从 OCR 识别的纸质合同导入；从 Markdown 导入
- **审批流程**：根据合同金额自动确定审批流程（普通/重大/特大），高风险合同阻断提交
- **风险审查**：AI 驱动的合同风险识别，标注风险等级（HIGH/MEDIUM/LOW）并给出修改建议
- **签章登记**：电子签章/用印登记，记录签章服务提供商、区块链交易 ID、签名数据
- **归档确认**：自动生成归档编号，锁定版本防篡改，Merkle 根哈希完整性校验
- **履约跟踪**：里程碑管理，自动标记逾期，30/7/1 天三级预警
- **版本管理**：自动版本号递增，HTML 清洗，SHA256 哈希校验，支持版本恢复与 DOCX 导出
- **Markdown 导入导出**：结构化 Markdown 格式双向转换，支持 YAML Front Matter、风险标注语法

### AI 能力

| 能力 | 模型 | 说明 |
|------|------|------|
| 合同起草 | qwen3-vl-plus | 根据合同类型、甲乙方、金额等生成结构化合同草稿 |
| 风险审查 | qwen3-vl-plus | 识别法律、履约、付款、知识产权、违约责任等风险，并写入合同风险等级 |
| 模板字段识别 | qwen3-vl-plus | 从合同模板中识别需填写的字段（甲方、金额、期限等） |
| OCR 识别 | PaddleOCR / 阿里云 | 将 PDF/DOCX 合同扫描件转为结构化文本 |

### 数据权限

基于角色的行级数据隔离，查询结果自动按权限过滤：

| 角色 | 编码 | 数据范围 |
|------|------|----------|
| 系统管理员 | ADMIN | ALL（全部数据） |
| 法务专员 | LEGAL | ALL |
| 财务专员 | FINANCE | ALL |
| 企业高管 | EXECUTIVE | ALL |
| 部门主管 | DEPT_LEADER | DEPT（本部门数据） |
| 普通员工 | USER | SELF（仅本人数据） |

---

## 项目结构

```
src/main/java/cupk/smartcontract/
├── SmartContractApplication.java
├── common/
│   ├── Result.java                # 统一响应封装
│   └── RoleEnum.java              # 六角色枚举 + 数据权限范围
├── config/
│   ├── MybatisPlusConfig.java     # MyBatis-Plus 拦截器（乐观锁 + 分页）
│   ├── OcrProperties.java         # OCR 配置属性
│   ├── QwenProperties.java        # 通义千问配置属性
│   ├── StorageProperties.java     # 本地存储配置属性
│   └── WebMvcConfig.java          # MVC 配置 + 拦截器注册
├── controller/
│   ├── AdminController.java       # 管理员 API（用户/角色/合同字段管理）
│   ├── ApprovalController.java    # 审批 API
│   ├── ArchiveController.java     # 归档 API
│   ├── AttachmentController.java  # 附件上传/OCR/关联/下载
│   ├── ContractController.java    # 合同 CRUD + AI 起草
│   ├── CounterpartyController.java # 交易对手方查询
│   ├── DashboardController.java   # 仪表盘汇总
│   ├── DeptController.java        # 部门查询
│   ├── FulfillmentController.java # 履约计划管理
│   ├── GlobalExceptionHandler.java # 全局异常处理
│   ├── LedgerController.java      # 台账（提交审批/签章/归档记录）
│   ├── MarkdownController.java    # Markdown 导入导出
│   ├── RiskController.java        # AI 风险审查
│   ├── SealController.java        # 签章登记
│   ├── TemplateController.java    # 合同模板管理
│   ├── UserController.java        # 用户认证（登录/注册/登出）
│   └── VersionController.java     # 合同版本管理
├── dto/                           # 26 个请求/响应 DTO
├── entity/
│   ├── BaseAuditEntity.java       # 审计字段基类
│   ├── ContractMain.java          # 合同主表
│   ├── ContractVersion.java       # 合同版本
│   ├── ContractTemplate.java      # 合同模板
│   ├── ContractAttachment.java    # 合同附件
│   ├── ContractKnowledge.java     # 合同知识库
│   ├── Approval.java              # 审批实例
│   ├── ApprovalRecord.java        # 审批操作记录
│   ├── RiskItem.java              # 风险项
│   ├── SealRecord.java            # 用印记录
│   ├── ArchiveRecord.java         # 归档记录
│   ├── BlockchainRecord.java      # 区块链存证记录
│   ├── FulfillmentPlan.java       # 履约计划
│   ├── ReminderRecord.java        # 提醒记录
│   ├── AiTaskRecord.java          # AI 任务记录
│   ├── OperationLog.java          # 操作日志
│   ├── FileInfo.java              # 文件元数据
│   ├── DeptInfo.java              # 部门信息
│   └── UserInfo.java              # 用户信息
├── mapper/                        # 18 个 MyBatis-Plus Mapper
├── security/
│   ├── AuthInterceptor.java       # JWT 认证拦截器
│   ├── RequireRole.java           # 角色权限注解
│   └── SecurityContext.java       # 线程级安全上下文
└── service/
    ├── AiDraftService.java        # AI 起草/风险审查/模板字段识别
    ├── ApprovalService.java       # 审批流程管理
    ├── AuthService.java           # 用户认证与角色管理
    ├── ContractAttachmentService.java # 附件全生命周期管理
    ├── ContractManagementService.java # 合同核心业务（CRUD/仪表盘/数据权限）
    ├── ContractVersionService.java    # 版本管理 + HTML 清洗 + DOCX 归档
    ├── CounterpartyService.java       # 交易对手方聚合查询
    ├── DeptService.java               # 部门查询
    ├── DocumentParseService.java      # PDF/DOCX 文档解析
    ├── DraftTemplateService.java      # 模板字段识别与填充
    ├── FileStorageService.java        # 本地文件存储（SHA256 去重）
    ├── FulfillmentService.java        # 履约计划管理
    ├── MarkdownContractService.java   # Markdown 导入导出
    ├── OcrService.java                # OCR 识别（PaddleOCR/阿里云）
    ├── StatusTransitionService.java   # 合同状态流转管控
    ├── TemplateService.java           # 合同模板管理
    ├── TokenService.java              # JWT 签发/校验
    └── WordArchiveService.java        # HTML 转 Word DOCX

src/main/resources/
├── application.properties
└── static/
    ├── html/                      # 15 个前端页面
    │   ├── index.html             # 入口跳转
    │   ├── login.html             # 登录
    │   ├── register.html          # 注册
    │   ├── dashboard.html         # 工作台（按角色展示）
    │   ├── draft.html             # 合同草稿列表
    │   ├── edit.html              # 在线编辑（富文本 + OCR）
    │   ├── ledger.html            # 合同台账
    │   ├── approval.html          # 审批中心（可视化流程线）
    │   ├── risk.html              # AI 风险审查
    │   ├── seal.html              # 签章登记（四步向导）
    │   ├── signature.html         # 电子签章（集成第三方）
    │   ├── archive.html           # 归档确认（三步向导）
    │   ├── fulfillment.html       # 履约预警（甘特图）
    │   ├── templates.html         # 合同模板管理
    │   └── users.html             # 用户管理
    ├── css/                       # 样式文件
    └── js/                        # 页面逻辑
```

---

## 快速启动

### 1. 创建数据库

导入种子数据：

```bash
mysql -u root -p < smartcontract.sql
```

### 2. 配置数据库连接

编辑 `src/main/resources/application.properties`：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/smart_contract_db?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=你的密码
```

### 3. 配置 AI 服务（可选）

```powershell
$env:DASHSCOPE_API_KEY="你的 DashScope API Key"
```

```properties
# 通义千问（阿里云百炼 OpenAI 兼容模式）
ai.qwen.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1
ai.qwen.model=qwen3-vl-plus
ai.qwen.enable-thinking=true
ai.qwen.thinking-budget=81920

# OCR（PaddleOCR 云服务，可选）
ai.ocr.paddle-token=${PADDLE_OCR_TOKEN:}
```

> 不配置 AI 服务时，系统仍可正常运行，AI 相关功能会返回提示信息。

### 4. 启动

```bash
./mvnw spring-boot:run
```

浏览器打开 **http://localhost:8080/**

---

## 内置账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | Admin@123 | ADMIN |
| legal | Legal@123 | LEGAL |
| user | User@123 | USER |
| dept_leader | Demo@123 | DEPT_LEADER |
| finance | Demo@123 | FINANCE |
| executive | Demo@123 | EXECUTIVE |

---

## API 接口

### 用户认证

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/users/login` | 登录 | 否 |
| POST | `/api/users/register` | 注册 | 否 |
| POST | `/api/users/logout` | 登出 | 否 |
| GET | `/api/users/current` | 当前用户 | 是 |

### 管理员

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/admin/users` | 用户列表 | ADMIN |
| PUT | `/api/admin/users/{userId}/role` | 更新用户角色 | ADMIN |
| GET | `/api/admin/roles` | 角色列表 | ADMIN |
| PUT | `/api/admin/contracts/{contractId}/fields` | 修改合同字段（状态/风险等级） | ADMIN |

### 合同管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/contracts` | 合同列表（支持关键词/状态/风险/类型筛选） | 登录 |
| POST | `/api/contracts` | 创建合同 | USER+ |
| PUT | `/api/contracts/{contractId}` | 更新合同（已归档禁止编辑） | 登录 |
| DELETE | `/api/contracts/{contractId}` | 删除合同（仅草稿） | USER+ |
| POST | `/api/contracts/{contractId}/submit` | 提交审批 | USER+ |

### AI 能力

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/ai/draft` | AI 起草合同 | USER+ |
| POST | `/api/ai/draft-stream` | AI 起草合同（SSE 流式） | USER+ |
| POST | `/api/ai/risk-review` | AI 风险审查 | LEGAL+ |

### 附件与 OCR

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/attachments/upload` | 上传附件（自动 OCR） | USER+ |
| GET | `/api/attachments` | 附件列表 | 登录 |
| POST | `/api/attachments/{id}/ocr` | 执行 OCR 识别 | USER+ |
| POST | `/api/attachments/{id}/link` | 关联附件到合同 | USER+ |
| POST | `/api/contracts/from-ocr` | 从 OCR 结果创建合同 | USER+ |
| GET | `/api/attachments/{id}/download` | 下载附件 | 登录 |

### 审批

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/approvals` | 审批记录列表 | 登录 |
| POST | `/api/approvals/{instanceId}/agree` | 同意审批 | DEPT_LEADER+ |

### 签章与归档

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/contracts/{contractId}/seal` | 签章登记 | LEGAL+ |
| POST | `/api/contracts/{contractId}/archive` | 归档确认 | LEGAL+ |
| GET | `/api/contracts/{contractId}/seal-records` | 签章记录 | 登录 |
| GET | `/api/contracts/{contractId}/archive-records` | 归档记录 | 登录 |

### 版本管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/contracts/{contractId}/versions` | 保存版本快照 | USER+ |
| GET | `/api/contracts/{contractId}/versions` | 版本列表 | 登录 |
| GET | `/api/contracts/{contractId}/versions/latest` | 最新版本 | 登录 |
| GET | `/api/contracts/{contractId}/versions/{versionId}` | 版本详情 | 登录 |
| POST | `/api/contracts/{contractId}/versions/{versionId}/restore` | 恢复历史版本 | USER+ |
| GET | `/api/contracts/{contractId}/versions/{versionId}/download` | 下载版本文件 | 登录 |

### Markdown 导入导出

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/contracts/{contractId}/export/markdown` | 导出 Markdown | USER+ |
| GET | `/api/contracts/{contractId}/export/markdown/preview` | 预览导出内容 | USER+ |
| POST | `/api/contracts/import/markdown/parse` | 解析 Markdown | USER+ |
| POST | `/api/contracts/import/markdown` | 从 Markdown 创建合同 | USER+ |
| GET | `/api/contracts/import/markdown/spec` | 格式说明 | 登录 |

### 模板管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/templates` | 模板列表 | 登录 |
| GET | `/api/templates/{id}` | 模板详情 | 登录 |
| POST | `/api/templates` | 创建模板 | LEGAL+ |
| PUT | `/api/templates/{id}` | 更新模板 | LEGAL+ |
| DELETE | `/api/templates/{id}` | 删除模板 | LEGAL+ |
| GET | `/api/templates/{id}/download` | 下载模板文件 | 登录 |

### 其他

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/dashboard` | 仪表盘汇总 | 登录 |
| GET | `/api/counterparties` | 交易对手方列表 | 登录 |
| GET | `/api/departments` | 部门列表 | 登录 |
| GET | `/api/fulfillment-plans` | 履约计划列表 | 登录 |
| PUT | `/api/fulfillment-plans/{planId}/status` | 更新履约状态 | 登录 |

> 权限说明：`USER+` = USER / DEPT_LEADER / LEGAL / FINANCE / EXECUTIVE / ADMIN；`LEGAL+` = LEGAL / EXECUTIVE / ADMIN；`DEPT_LEADER+` = DEPT_LEADER / LEGAL / EXECUTIVE / ADMIN；ADMIN 拥有全部权限。

---

## 合同审批阈值配置

```properties
# 重大合同最低金额（默认 10 万），达到后触发 Legal + Executive 会签
contract.threshold.major=100000
# 超阈值合同最低金额（默认 50 万），达到后触发 Legal + Executive 同时会签
contract.threshold.super=500000
```

---

## 合同模板分类

系统内置 8 种合同模板类型：保密合同、劳务合同、采购合同、销售合同、技术合同、物流合同、企业服务合同、知识产权合同。
