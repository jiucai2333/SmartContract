# Feature F Extraction Notes

This folder is limited to Member F responsibilities:

- JWT authentication and RBAC authorization.
- Six-role menu visibility and backend menu filtering.
- Operation audit logging for security-relevant actions.
- Centralized sensitive-data masking for logs, display, export, and AI prompt boundaries.
- AI compliance notice for generated drafts, summaries, and review reports.
- Placeholder configuration structures for OA, HR, and electronic-signature integrations.

## In Scope

- `RoleCode`, `RequireRole`, `AuthInterceptor`, and `SecurityContext`.
- `MenuService` and `/api/security/menus`.
- `AuditOperation`, `SecurityAuditAspect`, `AuditLogService`, and audit log query APIs.
- `SensitiveDataMasker`.
- `ComplianceAiService` and `/api/ai/risk-review`.
- `SecurityEventService` and `/api/security-events`, which only simulates the required audit event
  categories and does not implement contract business logic.
- Admin user-role maintenance used by RBAC.
- External integration config placeholders.

## Out of Scope

- Contract drafting, lifecycle, approval workflow, archive business logic, delivery, and payment
  business implementation.
- Real OA, HR, or electronic-signature API calls.
- Full database field encryption migration and formal classified protection assessment.

## Source Archive

`source-extracts/` contains copied original-project files that are directly related to this scope.
It is an archive for reference, not a full runnable copy of the original project.
