# Member F Source Extracts

This directory stores original-project files related to Member F only:

- Authentication, JWT, RBAC, and six-role definitions.
- Backend authorization annotations and interceptors.
- Role-aware frontend menu/user-role management code.
- Operation audit annotation, aspect, entity, mapper, service, and query controller.
- Sensitive-data masking utility.
- AI prompt masking, AI compliance notice, and shared display/export masking touchpoints.
- OA/HR/electronic-signature placeholder configuration.

The extracted files are grouped by original project area:

- `smartcontract/backend/common/`: role and data-scope definitions.
- `smartcontract/backend/security/`: auth, RBAC, audit aspect, and masking.
- `smartcontract/backend/service/`: auth/token/audit services plus AI/output security boundaries.
- `smartcontract/backend/controller/`: login, role admin, audit log query, and AI security entrypoints.
- `smartcontract/backend/entity/` and `smartcontract/backend/mapper/`: user and operation-log storage
  objects required by RBAC/audit.
- `smartcontract/frontend/`: role menu and admin role-management UI files.
- `smartcontract/config/`: safe config snippet with environment-variable placeholders.

These files are not intended to be a complete contract-management module. Business modules such as
contract lifecycle, approval workflow, archive, delivery, and payment should stay with their owners;
F only provides the shared security, audit, masking, and integration-boundary infrastructure.
