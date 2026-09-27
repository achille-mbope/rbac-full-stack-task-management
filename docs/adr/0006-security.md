# ADR-0006: Security Baseline and Authorization

**Status:** Accepted  
**Date:** 2026-09-15  
**Updated:** 2026-09-27

## Context

Credentials and organizational tasks require authentication and backend enforcement
of roles and assignment. User-created tasks are also visible to administrators.

## Decision

### Roles and task access

Version one has `USER` and `ADMIN` roles. Administrators manage accounts and can
create tasks assigned to any enabled user or administrator. Users can create their
own tasks, automatically assigned to themselves. All administrators may list, read,
update, and delete all tasks, regardless of creator or assignee. Tasks are organizational
work; v1 does not provide personal tasks hidden from administrators. This supersedes
the earlier owner-only and creating-admin-only access rules.

| Operation | USER | ADMIN |
| --- | --- | --- |
| Create automatically self-assigned tasks | Allowed | Allowed |
| List, read, update, delete assigned tasks | Allowed | Allowed |
| Create tasks assigned to users or admins | Denied | Allowed |
| List, read, update, delete all tasks | Denied | Allowed through admin routes |
| Change own password | Allowed | Allowed |
| List accounts, assign roles, enable/disable accounts | Denied | Allowed |

- Registration assigns `USER`; reject client-supplied roles.
- Provision the first administrator using a controlled operator procedure,
  documented during implementation. Never ship default administrator credentials.
- The `user` module enforces account-administration permissions. The `task`
  module enforces task access in application services and access-scoped queries.
- Ordinary creation derives assignee and creator from the authenticated subject.
  Only POST /api/v1/admin/tasks accepts an explicit assigneeId; check ADMIN before
  looking up the recipient. Accept enabled USER or ADMIN recipients, including self.
  Missing recipients return 404; disabled recipients return 409. Record the creator
  from the token, never from request data. Both IDs are immutable in v1.
- Personal `/tasks` routes are assignee-scoped for everyone, including ADMIN.
  Apply assignee restrictions before filtering, pagination, and counts. Missing
  and other-assignee tasks return identical 404 responses on these routes.
- Administrative `/admin/tasks` routes require ADMIN before task lookup and allow
  all tasks, including user-created tasks and tasks assigned to disabled accounts.
  Non-admin callers receive 403; authorized callers receive 404 for missing tasks.
  Creator identity is attribution only and never grants additional access.
- After an ADMIN role is removed, global access ends when existing ADMIN tokens
  expire. Assignee access is independent of role. Disabling an account does not
  revoke existing tokens or alter existing task assignments.
- Record administrative task edits and deletions synchronously in the same database
  transaction as the mutation: actor ID, task ID, UTC timestamp, action, and changed
  fields with before/after values. Deletion records must survive task deletion.
  Restrict audit access to authorized operators; never record credentials or tokens.
  Audit retrieval is outside the public v1 API; events remain deferred.
- Enforce permissions in the backend; Angular guards provide UX only.
- Deny unmatched protected routes by default.
- Existing JWTs retain previous authority until expiry as defined in
  [ADR-0003](0003-authentication.md).

### Credentials and HTTP security

- **Password hashing:** BCrypt strength 12; benchmark on deployment hardware.
- **Password policy:** at least 15 characters; allow spaces and Unicode without
  composition rules. Reject inputs over BCrypt's 72-byte UTF-8 limit instead of
  truncating. Revisit the algorithm if longer passphrases are required.
- **CORS:** only `http://localhost:4200` in development; explicit
  environment-configured origin allowlist in production. CORS is not authorization.
- **CSRF:** disable only while using ADR-0003's bearer-header-only model.
  Adding authentication cookies requires revisiting CSRF protection.
- **HTTPS:** required in deployed environments.
- **Rate limiting:** not implemented in v1. Login/registration remain susceptible
  to automated abuse; add protection before public deployment.
- **Secrets:** environment variables, with no committed defaults.
- **Logging:** never log passwords, JWTs, Authorization headers, or full auth
  request bodies. Login failures must not disclose whether an account exists or is disabled.

## Implementation status (2026-09-27)

Account HTTP authorization and JWT handling are implemented. Task application tests
cover self-assignment, assignee-scoped access for USER and ADMIN, admin checks before
lookup, enabled-recipient validation, and administrative audit requests. Principal
and persistence adapters and transactional audit storage are implemented. H2 integration
tests verify database rollback after audit flush and audit survival after deletion.
PostgreSQL verification is blocked by unavailable Docker; task HTTP endpoints remain
pending. Audit reads require restricted database/operator access; no public audit
retrieval endpoint is exposed. See the
[backend guide](../../backend/README.md#task-domain-and-next-steps).

## Rationale

Assignee checks isolate ordinary users' work. Explicit administrative routes support
global task supervision with role enforcement and mutation auditing. BCrypt is retained with explicit
input and cost constraints.

## Alternatives

- **Assignee-only or creating-admin-only access:** prevents other administrators
  from supervising organizational work; superseded by the global admin policy.
- **Private personal tasks:** would require a separate visibility policy, outside v1.
- **Argon2id:** preferred for a future password-storage revision, particularly
  if BCrypt's input limit conflicts with passphrase requirements.

## Consequences

- HTTP security configuration lives in `auth`; business authorization stays
  with the module owning the operation.
- Test user isolation, all-admin access, personal-route scoping, assignment authorization,
  recipient validation, role escalation, disabled-account login, and the accepted
  JWT stale-authorization window.
- Ownership denials follow [ADR-0004](0004-api-design.md).
- The README must document authentication limitations and administrator bootstrap.

## References

- [OWASP authentication](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
