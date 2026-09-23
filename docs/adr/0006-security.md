# ADR-0006: Security Baseline and Authorization

**Status:** Accepted  
**Date:** 2026-09-15  
**Updated:** 2026-09-23

## Context

Credentials and private tasks require both authentication and backend enforcement
of roles and ownership.

## Decision

### Roles and ownership

Version one has `USER` and `ADMIN` roles. Administrators manage accounts;
they do not receive access to other users' private tasks.

| Operation | USER | ADMIN |
| --- | --- | --- |
| Create tasks owned by the caller | Allowed | Allowed |
| List, read, update, delete own tasks | Allowed | Allowed |
| Access another user's tasks | Denied | Denied |
| Change own password | Allowed | Allowed |
| List accounts, assign roles, enable/disable accounts | Denied | Allowed |

- Registration assigns `USER`; reject client-supplied roles.
- Provision the first administrator using a controlled operator procedure,
  documented during implementation. Never ship default administrator credentials.
- The `user` module enforces account-administration permissions. The `task`
  module enforces ownership in application services and owner-scoped queries.
- Derive ownership from the authenticated subject, not a supplied owner ID.
  Task updates must not transfer ownership.
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

## Rationale

Role checks alone do not protect individual tasks. Ownership rules preserve
private data even from account administrators. BCrypt is retained with explicit
input and cost constraints.

## Alternatives

- **Admin access to all tasks:** unnecessary for account administration.
- **Argon2id:** preferred for a future password-storage revision, particularly
  if BCrypt's input limit conflicts with passphrase requirements.

## Consequences

- HTTP security configuration lives in `auth`; business authorization stays
  with the module owning the operation.
- Test cross-user access, role escalation, disabled-account login, and the accepted
  JWT stale-authorization window.
- Ownership denials follow [ADR-0004](0004-api-design.md).
- The README must document authentication limitations and administrator bootstrap.

## References

- [OWASP authentication](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
