# Feature F Security Audit

This standalone Spring Boot project extracts and improves the original Feature F code:

- role-based menus
- role-based API access with `@RequireRole`
- audit logging for Feature F security events
- centralized sensitive data masking before audit details are stored
- AI risk-review prompt masking before any model call boundary
- OA / HR / electronic-signature configuration placeholders

## Folder Layout

- `src/`: runnable Spring Boot demo for this feature set.
- `source-extracts/`: code copied from the original `0611/` project that is directly related to
  role menus, RBAC/JWT, operation audit, sensitive masking, AI prompt masking, and integration
  placeholders.

## Why this is different from the source project

The source project already has `RoleEnum`, `@RequireRole`, `AuthInterceptor`, `@AuditOperation`,
`SecurityAuditAspect`, `OperationLog`, `SecurityAuditService`, and front-end menu filtering in
`static/js/common.js`.

This extraction keeps those ideas but tightens the design:

- Menus are filtered by the backend at `/api/security/menus`; the browser no longer trusts only
  `localStorage.roleCode`.
- The audit aspect covers all REST controller methods, while `@AuditOperation` can still override
  operation names, target type, and target id.
- `/api/security-events` is a Feature F audit gateway for the required event categories:
  contract create/update, version restore, AI review, approval action, signing file upload,
  archive, and delivery/payment change.
- Audit details pass through `SensitiveDataMasker`, which masks passwords, tokens, API keys,
  phone numbers, ID cards, bank accounts, emails, and bearer tokens.
- AI review uses a dedicated masking gate: contract text is converted to placeholders such as
  `[ID_CARD]`, `[MOBILE]`, `[BANK_ACCOUNT]`, and `[EMAIL]`, then checked again before the model
  boundary. If residual sensitive data remains, the request is blocked.
- AI generated content carries `AI_COMPLIANCE_NOTICE`, and the demo export reuses the same masking
  utility used by screen display and audit logs.
- `targetId` uses SpEL such as `#p0.targetId`, `#p0`, and `#a0` when parameter names are
  unavailable.
- JWT signature comparison uses constant-time comparison and the secret must be at least 32 bytes.
- `integration.oa`, `integration.hr`, and `integration.electronic-signature` are present as
  placeholder configuration structures only.
- The project uses in-memory users and logs so it can run without MySQL or Redis.

## Run

```powershell
mvn test
mvn spring-boot:run
```

Open:

```text
http://localhost:8091/
```

Seed users:

```text
admin / Admin@123
legal / Legal@123
leader / Leader@123
user / User@123
```

Use `admin` to view audit logs. Record a security audit event with the sample phone, ID card, and
bank account values, then refresh audit logs to see masked request details. Use `legal`,
`executive`, or `admin` to run the AI risk-review demo and inspect the sanitized prompt.

## Important files

```text
src/main/java/demo/featuref/common/RoleCode.java
src/main/java/demo/featuref/security/RequireRole.java
src/main/java/demo/featuref/security/AuthInterceptor.java
src/main/java/demo/featuref/security/AuditOperation.java
src/main/java/demo/featuref/security/SecurityAuditAspect.java
src/main/java/demo/featuref/util/SensitiveDataMasker.java
src/main/java/demo/featuref/service/ComplianceAiService.java
src/main/java/demo/featuref/service/SecurityEventService.java
src/main/java/demo/featuref/service/MenuService.java
src/main/java/demo/featuref/service/AuditLogService.java
src/main/java/demo/featuref/config/ExternalIntegrationProperties.java
src/main/resources/static/js/security-menu.js
```
