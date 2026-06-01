# 智能合同管理系统

基于 Spring Boot 的用户认证与角色管理系统，支持 JWT 登录注册、RBAC 六角色权限管控。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Java 17 + Spring Boot 3.5.14 |
| ORM | MyBatis-Plus 3.5.12 |
| 数据库 | MySQL 8.0 |
| 认证 | 自签 JWT (HMAC-SHA256) + BCrypt |
| 前端 | 原生 HTML / CSS / JavaScript |

---

## 项目结构

```
src/main/java/cupk/smartcontract/
├── SmartContractApplication.java
├── common/
│   ├── Result.java              # 统一响应封装
│   ├── RoleEnum.java            # 六角色枚举
│   ├── SecurityContext.java     # 线程级安全上下文
│   └── RequireRole.java         # 角色权限注解
├── config/
│   ├── AuthInterceptor.java     # JWT 认证拦截器
│   ├── WebMvcConfig.java        # MVC 配置
│   └── MybatisPlusConfig.java   # MyBatis-Plus 配置
├── domain/
│   ├── BaseAuditEntity.java     # 审计字段基类
│   └── UserInfo.java            # 用户实体
├── dto/
│   ├── AuthUserVO.java          # 认证用户视图
│   ├── LoginRequest.java        # 登录请求
│   ├── RegisterRequest.java     # 注册请求
│   └── RoleVO.java              # 角色视图
├── mapper/
│   └── UserInfoMapper.java      # 用户 Mapper
├── service/
│   ├── AuthService.java         # 登录注册 + 用户管理
│   └── TokenService.java        # JWT 签发/校验
└── web/
    ├── UserController.java      # 用户 API
    └── AdminController.java     # 管理员 API

src/main/resources/
├── application.properties
└── static/
    ├── html/                    # 登录/注册/工作台/用户管理
    ├── css/                     # 样式文件
    └── js/                      # 页面逻辑
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

### 3. 启动

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
| POST | `/api/user/login` | 登录 | 否 |
| POST | `/api/user/register` | 注册 | 否 |
| POST | `/api/user/logout` | 登出 | 是 |
| GET | `/api/user/current` | 当前用户 | 是 |

### 管理员

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/users` | 用户列表 |
| PUT | `/api/admin/users/{id}/role` | 更新用户角色 |
| GET | `/api/admin/roles` | 角色列表 |

---

## 角色体系

| 角色 | 编码 | 数据范围 |
|------|------|----------|
| 系统管理员 | ADMIN | ALL |
| 法务专员 | LEGAL | ALL |
| 财务专员 | FINANCE | ALL |
| 企业高管 | EXECUTIVE | ALL |
| 部门主管 | DEPT_LEADER | DEPT |
| 普通员工 | USER | SELF |
