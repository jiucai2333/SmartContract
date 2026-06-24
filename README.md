<div align="center">

# SmartContract

### AI 驱动的合同全生命周期管理平台

从合同起草、智能审查到审批、签章、归档与履约追踪，
为中小企业提供一套可落地、可审计、可扩展的合同管理方案。

<p>
  <a href="#快速开始"><strong>快速开始</strong></a>
  ·
  <a href="#核心能力"><strong>核心能力</strong></a>
  ·
  <a href="#系统架构"><strong>系统架构</strong></a>
  ·
  <a href="#配置指南"><strong>配置指南</strong></a>
</p>

<p>
  <img alt="Java 17" src="https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white">
  <img alt="Spring Boot 3.5" src="https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white">
  <img alt="Vue 3" src="https://img.shields.io/badge/Vue-3-42B883?style=for-the-badge&logo=vuedotjs&logoColor=white">
  <img alt="MySQL 8" src="https://img.shields.io/badge/MySQL-8-4479A1?style=for-the-badge&logo=mysql&logoColor=white">
</p>

</div>

---

## 为什么是 SmartContract

合同管理不只是保存文件。SmartContract 将合同正文、审批记录、风险报告、签章状态、履约计划和审计日志组织在同一条业务链路中，并通过大模型与 OCR 降低起草和审查成本。

```text
起草 ──▶ AI 审查 ──▶ 审批 ──▶ 签章 ──▶ 归档 ──▶ 履约 ──▶ 完成
 DRAFT     REVIEW    APPROVING  SIGNING   ARCHIVED  EXECUTING  COMPLETED
```

> 项目适合课程设计、毕业设计、企业内部原型验证，以及合同数字化流程的二次开发。

## 核心能力

<table>
  <thead>
    <tr>
      <th width="44"></th>
      <th>能力</th>
      <th>说明</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><img src="docs/icons/ai.svg" width="22" alt=""></td>
      <td><strong>AI 合同助手</strong></td>
      <td>使用通义千问完成合同起草、字段识别、风险审查与合规分析</td>
    </tr>
    <tr>
      <td><img src="docs/icons/document.svg" width="22" alt=""></td>
      <td><strong>OCR 文档导入</strong></td>
      <td>识别 PDF、DOCX 等附件，支持版式还原、方向校正与表格识别</td>
    </tr>
    <tr>
      <td><img src="docs/icons/approval.svg" width="22" alt=""></td>
      <td><strong>分级审批</strong></td>
      <td>根据合同金额与角色配置普通、重大和超阈值审批路径</td>
    </tr>
    <tr>
      <td><img src="docs/icons/signature.svg" width="22" alt=""></td>
      <td><strong>电子签章</strong></td>
      <td>对接法大大 FASC v5 API，并提供本地降级实现</td>
    </tr>
    <tr>
      <td><img src="docs/icons/archive.svg" width="22" alt=""></td>
      <td><strong>归档与存证</strong></td>
      <td>保存合同版本、生成归档编号并记录区块链存证信息</td>
    </tr>
    <tr>
      <td><img src="docs/icons/progress.svg" width="22" alt=""></td>
      <td><strong>履约追踪</strong></td>
      <td>管理履约计划、交付物、付款记录、进度和逾期预警</td>
    </tr>
    <tr>
      <td><img src="docs/icons/security.svg" width="22" alt=""></td>
      <td><strong>权限与审计</strong></td>
      <td>JWT 认证、角色权限、数据范围、操作审计与敏感信息脱敏</td>
    </tr>
  </tbody>
</table>

## 产品模块

| 模块 | 主要功能 |
|---|---|
| 工作台 | 合同指标、趋势、待办事项和到期预警 |
| 合同编制 | 草稿管理、在线编辑、模板填充、Markdown / DOCX 导入导出 |
| 智能审查 | AI 风险识别、风险分级、合规分析和报告导出 |
| 审批中心 | 多级审批、同意、驳回和审批历史 |
| 签章归档 | 用印登记、电子签章、归档锁定和存证记录 |
| 履约管理 | 履约计划、交付物、付款台账和逾期提醒 |
| 系统管理 | 用户角色、部门、审计日志和安全事件 |

## 技术栈

| 层级 | 技术 |
|---|---|
| 前端 | Vue 3 · Vite 6 · Vue Router · Pinia |
| 后端 | Java 17 · Spring Boot 3.5 · Spring MVC |
| 数据 | MySQL 8 · MyBatis-Plus 3.5 · Redis（可选） |
| AI / OCR | 通义千问 · PaddleOCR-VL |
| 文档 | Apache POI · PDFBox · Jsoup |
| 安全 | JWT · BCrypt · 角色与数据权限 · 操作审计 |
| 集成 | 法大大 FASC v5 · 区块链存证接口 |

## 系统架构

```text
┌─────────────────────────────────────────────────────────┐
│                    Vue 3 / Vite SPA                     │
│  工作台 · 合同编辑 · 智能审查 · 审批 · 签章 · 履约       │
└──────────────────────────┬──────────────────────────────┘
                           │ REST / SSE
┌──────────────────────────▼──────────────────────────────┐
│                    Spring Boot API                      │
│  认证授权 · 合同服务 · 审批流 · 文档处理 · 审计追踪      │
└───────────┬──────────────────┬──────────────────┬───────┘
            │                  │                  │
     ┌──────▼──────┐    ┌──────▼──────┐    ┌──────▼──────┐
     │ MySQL / Redis│    │ Qwen / OCR  │    │ 法大大 / 存证 │
     └─────────────┘    └─────────────┘    └─────────────┘
```

<details>
<summary><strong>查看项目目录</strong></summary>

```text
SmartContract/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/cupk/smartcontract/
│       │   ├── config/       # 应用与第三方服务配置
│       │   ├── controller/   # REST API
│       │   ├── service/      # 核心业务逻辑
│       │   ├── security/     # 认证、授权、审计与脱敏
│       │   ├── entity/       # 数据库实体
│       │   └── mapper/       # MyBatis Mapper
│       └── test/             # 后端测试
├── frontend/
│   ├── src/
│   │   ├── views/            # 页面
│   │   ├── components/       # 公共组件
│   │   ├── stores/           # Pinia 状态
│   │   └── api/              # 请求封装
│   └── vite.config.js
├── sql/                      # 建表与迁移脚本
└── data/uploads/             # 本地附件目录
```

</details>

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 20+
- MySQL 8.0+
- Redis 7.0+（可选）

### 1. 获取代码

```bash
git clone https://github.com/jiucai2333/SmartContract.git
cd SmartContract
```

### 2. 初始化数据库

```sql
CREATE DATABASE smart_contract_db
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

随后导入 `sql/` 目录中的建表与初始化脚本。

### 3. 配置环境变量

最少需要提供数据库密码。AI、OCR 和签章能力按需配置。

#### Windows PowerShell

```powershell
$env:DB_PASSWORD="your-mysql-password"
$env:DASHSCOPE_API_KEY="your-dashscope-key"
$env:PADDLE_OCR_TOKEN="your-paddle-token"
```

#### macOS / Linux

```bash
export DB_PASSWORD="your-mysql-password"
export DASHSCOPE_API_KEY="your-dashscope-key"
export PADDLE_OCR_TOKEN="your-paddle-token"
```

### 4. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端默认运行在 [http://localhost:8080](http://localhost:8080)。

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

打开 [http://localhost:5173](http://localhost:5173)，Vite 会将 `/api` 请求代理至 `8080`。

### 6. 登录

| 账号 | 角色 | 默认密码 |
|---|---|---|
| `admin` | 系统管理员 | `@123` |
| `legal` | 法务专员 | `@123` |
| `finance` | 财务专员 | `@123` |
| `executive` | 企业高管 | `@123` |
| `dept_leader` | 部门主管 | `@123` |
| `user` | 普通员工 | `@123` |

> 默认账号仅用于本地演示。正式部署前请修改密码并禁用不需要的账号。

## 配置指南

所有敏感信息都应通过环境变量注入，不要将真实密钥提交到仓库。

| 环境变量 | 必需 | 用途 |
|---|:---:|---|
| `DB_PASSWORD` | 是 | MySQL `root` 用户密码 |
| `DASHSCOPE_API_KEY` | 否 | 通义千问起草、识别和审查 |
| `PADDLE_OCR_TOKEN` | 否 | PaddleOCR 云服务 |
| `FADADA_APP_ID` | 否 | 法大大应用 ID |
| `FADADA_APP_SECRET` | 否 | 法大大应用密钥 |
| `FADADA_CORP_ID` | 否 | 法大大企业 ID |
| `FADADA_CALLBACK_URL` | 否 | 签章结果公网回调地址 |

核心配置位于 `backend/src/main/resources/application.properties`：

```properties
server.port=8080
spring.profiles.default=mysql
spring.datasource.password=${DB_PASSWORD:}

ai.qwen.api-key=${DASHSCOPE_API_KEY:}
ai.ocr.paddle-token=${PADDLE_OCR_TOKEN:}

signature.fadada-app-id=${FADADA_APP_ID:}
signature.fadada-app-secret=${FADADA_APP_SECRET:}
signature.fadada-corp-id=${FADADA_CORP_ID:}
signature.fadada-callback-url=${FADADA_CALLBACK_URL:}
```

## 开发与验证

```bash
# 后端测试
cd backend
mvn test

# 前端生产构建
cd frontend
npm run build
```

当前基线验证结果：

- 后端：56 项测试通过
- 前端：Vite 生产构建通过
- 默认端口：前端 `5173`，后端 `8080`

## 安全说明

- 除登录和注册外，`/api/**` 默认要求 JWT。
- 权限模型包含 `ADMIN`、`LEGAL`、`FINANCE`、`EXECUTIVE`、`DEPT_LEADER` 和 `USER`。
- 数据范围分为 `SELF`、`DEPT` 和 `ALL`。
- `@AuditOperation` 记录关键业务操作，安全事件单独留痕。
- 日志中的密码、Token、API Key 和合同正文会进行脱敏。

## 参与开发

欢迎通过 Issue 或 Pull Request 提交问题与改进。建议提交前运行：

```bash
mvn test
npm run build
```

提交信息推荐使用 Conventional Commits：

```text
feat(contract): add contract template import
fix(auth): handle expired access token
docs(readme): improve getting started guide
```

## License

当前仓库尚未声明开源许可证。在添加明确许可证前，默认保留全部权利。

---

<div align="center">

**让合同从起草到履约，每一步都有迹可循。**

</div>
