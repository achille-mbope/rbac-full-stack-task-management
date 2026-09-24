# Changelog

Notable project changes are recorded here, newest first. The project has no tagged
releases. Backend implementation has started; the HTTP API and frontend remain planned.

## Unreleased

### User module DDD refactor

- Organized user-module internals into domain, application, application ports, and
  infrastructure packages while preserving the public module contracts.
- Added a framework-independent account model and immutable role values that enforce
  the mandatory USER grant; moved password policy into the domain layer.
- Routed all account use cases through `AccountStore` and introduced ports for
  the current principal, password hashing, and email normalization.
- Isolated JPA mapping and duplicate-email error translation in the persistence
  adapter, preserving transaction boundaries and updates to changed columns only.
- Reorganized tests alongside their owning layers, added dependency-boundary checks,
  and shared account contract tests between H2 and PostgreSQL.
- Verified compilation and 53 Docker-free tests. Full PostgreSQL verification remains
  blocked by an unavailable Docker environment.
- Updated backend architecture and testing documentation. HTTP controllers and JWT
  integration remain pending.

### User module implementation

- Added closed user-module APIs for registration, credential verification, own-password
  changes, account lookup, and ADMIN-only account listing, roles, and enabled status.
- Added BCrypt strength 12, Unicode-aware password validation, normalized unique email
  storage, immutable public account views, and service-level authorization.
- Added the initial Flyway migration, disposable H2 and persistent PostgreSQL profiles,
  PostgreSQL Testcontainers tests, and generated Modulith boundary documentation.
- Used test-first cycles for validation and account behavior; covered concurrent
  duplicate registration and consistent duplicate errors in PostgreSQL and H2.
- Corrected the scaffold's Testcontainers import for Boot 3 and pinned PostgreSQL 16.
  Removed the unused Modulith JPA event starter because events remain deferred.
- Documented setup, module interfaces, and operator promotion/recovery. REST adapters,
  JWT security, and initial administrator registration await auth integration.

### Added

- This changelog, linked from the README.
- Draft OpenAPI 3.1 contract in `docs/api/openapi.json` for registration, login,
  password changes, assigned task CRUD, and administrator account management.
- API contract guide documenting task fields and lifecycle, pagination, validation,
  error identifiers, authentication semantics, and draft design choices.
- Administrator task creation and assignment to enabled users or other admins via
  `POST /api/v1/admin/tasks`, with immutable assignee and creator IDs.
- Paginated global task listing at `GET /api/v1/admin/tasks`, with assignee,
  status, and title filters, plus `GET`, `PATCH`, and `DELETE` operations at
  `/api/v1/admin/tasks/{taskId}`.

### Changed

- Users and admins create automatically self-assigned tasks through `POST /api/v1/tasks`.
  Personal task routes remain assignee-scoped. All admins can list, view, edit, and
  delete any task through `/api/v1/admin/tasks`, superseding the earlier creator-only
  rule. User-created tasks are also visible to admins.
- Documented transactional audit requirements for admin task edits/deletions and
  aligned the README, API guide, and architecture, API, security, and module ADRs.
- Allowed the `task` module to use the public `user` API for synchronous recipient
  validation. Events remain deferred.
- Documented client-side logout: the frontend clears its in-memory JWT, with no
  logout endpoint or immediate server-side revocation. Copied tokens remain valid
  until their 15-minute expiry; password, role, and account-status changes also
  leave existing tokens valid until expiry.

### 2026-09-23 — Architecture and security decisions

Source: commit `38feebc`, merged in pull request #1 as `17ed456`.

#### Added

- Seven accepted architecture decision records in [docs/adr](docs/adr/):
  - [ADR-0001](docs/adr/0001-architecture.md): Spring Boot modular monolith,
    separate Angular SPA, and monorepo layout.
  - [ADR-0002](docs/adr/0002-database.md): disposable H2 development profile,
    PostgreSQL 16 persistence, Flyway migrations, and PostgreSQL Testcontainers checks.
  - [ADR-0003](docs/adr/0003-authentication.md): HS256 JWT authentication with
    15-minute access tokens stored in frontend memory, no refresh tokens, and
    explicit expiry and revocation limitations.
  - [ADR-0004](docs/adr/0004-api-design.md): REST endpoints under `/api/v1`,
    RFC 9457 Problem Details errors, and planned OpenAPI documentation.
  - [ADR-0005](docs/adr/0005-build-tool.md): Java 21, Spring Boot 3.5.x,
    Spring Modulith 1.4.x, Angular 20, Node.js 22, and reproducible Maven/npm builds.
  - [ADR-0006](docs/adr/0006-security.md): `USER` and `ADMIN` permissions,
    owner-only task access even for administrators, credential and HTTP security
    requirements, and controlled administrator provisioning.
  - [ADR-0007](docs/adr/0007-modulith.md): closed `auth`, `user`, `task`, and
    `common` modules, explicit dependency rules, and planned module verification.

#### Changed

- Expanded the [README](README.md) with project status, planned v1 scope,
  role permissions, technology baselines, architecture, database profiles,
  authentication tradeoffs, and a delivery checklist.
- Documented prerequisites and planned verification commands, clarifying that
  backend/frontend scaffolding, build files, tests, and Docker Compose are pending.
- Recorded v1 exclusions, including shared or assigned tasks, a separate viewer
  role, and CI configuration; documented rate limiting as required before public deployment.
