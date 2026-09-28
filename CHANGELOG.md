# Changelog

Notable project changes are recorded here, newest first. The project has no tagged
releases. Account HTTP endpoints and JWT authentication are implemented, along with
task domain, application logic, persistence, transactional audit wiring, and HTTP
endpoints and runtime OpenAPI/Swagger UI. The Angular frontend has a routing/layout
scaffold and development API proxy; authentication and task/account integration
remain pending. Older entries describe the state at
their implementation milestone; the newest entry records current verification.

## Unreleased

### Frontend routing and application layout

- Built on the Angular 21 CSR scaffold with a responsive navigation shell, skip
  link, active-route indicators, page titles, and lazy overview/task/user pages.
- Added the development `/api/**` proxy to Spring Boot on port 8080 and registered
  HttpClient for future API services. Preview pages load no protected account/task data.
- Replaced starter-template tests with routing checks for redirects, navigation,
  and lazy pages; production build and all six frontend tests passed.
- Added frontend setup, proxy, and hosting instructions. Authentication, guards,
  and task/account operations remain subsequent work.
- Updated architecture and deployment guidance for CSR, static hosting, API forwarding,
  and deep-link fallback, and added the two-terminal development workflow.
- Verified on 2026-09-29: production build and six tests passed; live proxy forwarding
  returned the backend's expected 401 Problem Details and direct `/tasks` loading
  served the app shell. Visual browser review and authenticated end-to-end flows
  remain pending.
### Database documentation

- Added a database guide derived from Flyway V1/V2 and persistence adapters, with a
  Mermaid ER diagram, table ownership, constraints, indexes, and query behavior.
- Documented scalar account references, audit IDs without foreign keys, deletion
  behavior, shared task/audit transactions, and database versus application rules.
- Linked migration/profile guidance and verification commands to existing setup
  and deployment runbooks, and added the guide to the documentation index and READMEs.

### Architecture diagrams

- Added Mermaid diagrams for allowed backend module dependencies, task request
  processing with transactional audit writes, and production transport alternatives
  with the proxy trust boundary.
- Kept diagrams beside their detailed module, security, and deployment guidance.

### Backend handoff and release documentation

- Updated project and backend READMEs to distinguish completed backend work,
  pending frontend integration, and deployment-specific release checks.
- Corrected the delivery checklist for implemented backend scaffolding, HTTP error
  coverage, and packaged production verification; retained unverified end-to-end
  and fresh-checkout items.
- Added a public release checklist covering transport, secrets, backups, request
  limits, monitoring, dependency review, and shared limits for multiple instances.
- Reworded API verification guidance as ongoing regression requirements and linked
  the remaining-work guide from the documentation index. Existing verification
  remains 248 passing tests and a successful packaged TLS smoke test.

### Production verification and reproducible local PostgreSQL

- Replaced the shadowing test `application.properties` with an explicitly activated
  test profile containing only overrides; tests now inherit the main base settings.
- Added real-server trusted-proxy tests for secure health/API access and resolved
  client rate-limit isolation, alongside the existing untrusted-header rejection tests.
- Added local PostgreSQL Compose with loopback binding, a persistent named volume,
  required password, health check, environment template, and setup guide.
- Added `backend/smoke-prod.ps1` to run the packaged production JAR with isolated
  PostgreSQL and certificate-verified TLS, exercise registration/login and task CRUD,
  check authorization/documentation access, and clean up its resources and secrets.
- Verified on 2026-09-28: `clean verify` passed 248 tests (218 Docker-free and
  30 PostgreSQL-backed), with no failures, errors, or skips. The packaged TLS smoke
  test also passed. Actual deployment infrastructure still needs release validation.

### Production hardening, rate limiting, and diagnostics

- Added a prod profile that selects PostgreSQL and rejects unsafe settings before
  database initialization, including H2, schema mutation, Swagger exposure, disabled
  protection, and unsafe error/management disclosure. Requires direct TLS or
  explicitly trusted native proxy processing and HTTPS CORS origins.
- Added bounded per-client and process-wide login/registration limits before
  request parsing/password hashing, with 429 Problem Details and Retry-After.
  Documented fixed-window, restart, NAT, and multi-instance limitations.
- Added minimal Actuator health/readiness/liveness endpoints with restricted
  management access, graceful shutdown, generated request IDs, secret-safe
  unexpected-error diagnostics, and low-cardinality rejection metrics.
- Extended the API contract with authentication 429 responses. Added configuration,
  concurrency, CORS, diagnostics, PostgreSQL production-profile, and real-server
  proxy-spoofing tests. Updated Docker-free test selectors for the new Docker suite.
- Added a production deployment runbook and updated READMEs and security/API ADRs.
- Added a documentation index, clarified `postgres` versus `prod` activation in
  the database ADR and setup guides, and documented manual-test rate limits and
  HTTPS-required API errors.
- Verified on 2026-09-28: `clean verify` passed 246 tests (218 Docker-free and
  28 PostgreSQL-backed), with no failures, errors, or skips, and packaged the JAR.

### Runtime OpenAPI and Swagger UI

- Added springdoc-openapi 2.8.17 with generated OpenAPI 3.1 JSON/YAML and Swagger UI.
- Documented all account/task operations, bearer authentication, ADMIN requirements,
  domain validation, nullable PATCH fields, Problem Details, and response headers.
- Enabled public documentation for local H2 development; disabled it by default
  elsewhere. `API_DOCS_ENABLED` controls availability, with disabled routes denied
  even to administrators. Swagger UI does not persist bearer tokens.
- Added independent comparison of all 16 operations with the checked-in contract,
  UI/configuration checks, and enabled/disabled documentation security tests.
  Generated JSON is written to `backend/target/openapi.json` for review.
- Updated README files, API documentation, and API/security ADRs with documentation
  URLs, profile/override access rules, bearer-token usage, metadata ownership, and
  the contract maintenance workflow. Recorded the focused documentation checks
  alongside full-build verification results.
- Full `clean verify` passed 207 tests (182 Docker-free and 25 PostgreSQL-backed),
  with no failures, errors, or skips; the executable JAR was packaged.


### PostgreSQL verification

- Ran all 24 PostgreSQL-backed tests successfully with Docker access, including the
  nine task persistence tests on PostgreSQL 16.15 (`postgres:16-alpine`).
- Verified Flyway V1/V2 migration execution, Hibernate schema validation, V2 constraints,
  scoped literal filtering/pagination, audit survival after deletion, and rollback of
  both task and audit writes after an audit flush. No production-code fixes were needed.
- Diagnosed the earlier Docker and Maven-cache failures as restricted-process access
  errors. Running Maven with access to Docker and the dependency cache resolved them.
- Completed `.\mvnw.cmd clean verify`: all 196 tests passed with no failures, errors,
  or skips, and the executable JAR was packaged. Updated current README, API, and ADR
  verification notes to replace the earlier blocked status.

### Task HTTP API

- Added personal and administrative CRUD controllers with dedicated creation,
  assignment, partial-update, task-response, and page-response DTOs.
- Implemented creation defaults, strict calendar-date input, omitted-versus-null
  update handling, followable Location headers, and empty 204 deletion responses.
- Added task Problem Details mapping and HTTP tests with signed JWTs covering
  authorization, assignment, attribution spoofing, validation, filtering, and auditing.
- Updated endpoint documentation, the task API walkthrough, OpenAPI implementation
  status, and API/security/module ADRs. Runtime OpenAPI/Swagger UI remains pending.
- Documented HTTP component ownership, response/error outcomes, DTO field-presence
  semantics, and the standalone task HTTP test command; linked the walkthrough and
  executable request examples from the README and API guide.
- All 44 new task HTTP tests and all 172 Docker-free regression tests passed through
  Surefire. Maven dependency-cache access errors still prevent a normal clean-build
  verification; PostgreSQL verification remains blocked by unavailable Docker.

### Task persistence and transactional wiring

- Added JPA task storage with scoped queries, literal title filtering, filtered totals,
  stable pagination, immutable attribution, and dynamic updates.
- Added the V2 PostgreSQL migration for task/audit tables, constraints, account foreign
  keys, and indexes. Audit records retain scalar IDs and survive task deletion.
- Implemented synchronous audit persistence, the Spring Security principal adapter,
  and transactional service wiring. Storage/audit adapters require a shared transaction.
- Added shared H2/PostgreSQL integration tests, including rollback after audit flush.
  All eight H2 integration tests passed; nine PostgreSQL tests could not start because
  Testcontainers found no running Docker environment. PostgreSQL verification remains pending.
- Updated implementation status and Docker-free test selectors. Task HTTP adapters are next.
- Aligned module summaries and API availability documentation with the persistence
  milestone, and added a verification table distinguishing H2 results from blocked
  PostgreSQL checks and the unverified clean build.
- All 128 Docker-free regression tests passed through Surefire, including account
  HTTP/JWT tests with the new task wiring. The normal build still reports local
  dependency-cache access errors; compiled classes were tested directly.

### Task application business logic

- Added personal and administrative task use-case contracts, immutable read models,
  creation/update inputs, and task-owned storage, current-principal, and audit ports.
- Implemented self-assignment, assignee-scoped personal access (including ADMIN),
  admin checks before lookup, and enabled-recipient validation through `AccountLookup`.
- Implemented partial-update field presence, empty-update rejection, pagination and
  title-filter validation, and synchronous admin edit/delete audit requests.
- Added in-memory application tests for access isolation, assignment, updates,
  scoped queries, and audit details/failure propagation. Services declare transaction
  boundaries; adapter wiring and database rollback verification remain pending.
- All 32 focused application, domain, and architecture tests passed via Surefire.
  Compilation produced current classes but reported the existing dependency-cache
  JAR-close access errors; a normal clean build remains unverified.
- Updated implementation documentation: persistence, principal/audit adapters, and
  transactional bean wiring are next, followed by HTTP adapters.
- Aligned the README delivery checklist, API implementation notes, OpenAPI status
  description, and API/security/module ADRs with completed task application logic
  and the remaining runtime and integration work.

### Task domain and implementation sequence

- Added the closed `task` module with a framework-independent `Task` domain model,
  public `TaskStatus`, and safe `TaskValidationException` details.
- Implemented immutable assignee/creator attribution, Unicode title and description
  limits, optional calendar due dates, creation defaults, restoration, and atomic
  updates. All status transitions, including reopening DONE, are allowed.
- Added 11 domain tests and task-module discovery verification. All 15 focused
  domain, layer, and Modulith tests passed through Maven Surefire on 2026-09-27.
  Wrapper startup and dependency-cache access errors prevented a normal clean-build
  verification; the focused run used compiled classes and cached Maven 3.9.16.
- Updated the README, backend guide, and API guide with implemented scope and the
  next step: application business logic and ports tested with in-memory fakes,
  followed by persistence adapters and the HTTP API. Task authorization, assignment
  lookup, and transactional administrative audit storage remain pending.

### Postman authentication walkthrough

- Added backend startup, registration and login requests, bearer-token handling,
  expected error responses, and H2 restart behavior to the backend guide.
- Linked the walkthrough from the main README and API documentation and documented
  the focused account HTTP/JWT test command.

### HTTP security documentation

- Updated the main README and backend guide to describe `HttpSecurityConfiguration`:
  servlet-only activation, stateless requests with request caching disabled,
  public registration/login, ADMIN-only administrative routes, and authentication
  for all other routes.
- Documented conversion of the JWT `roles` claim to `ROLE_`-prefixed authorities
  and summarized the disabled authentication mechanisms in the main README.

### Local development startup

- Added an idempotent `backend/setup-local.ps1` to generate a machine-local signing
  key in a Git-ignored file, imported only by the H2 development profile.
- After one-time setup, H2 starts with `.\mvnw.cmd spring-boot:run` without
  setting a signing key in each terminal. External keys override the local file;
  PostgreSQL/deployed profiles still require external key configuration.

### Clean-code refactor

- Clarified implemented versus planned features, security-component ownership,
  route authorization order, and clean-build/PostgreSQL verification instructions.

- Split account HTTP handling into registration, password, and administration
  controllers with dedicated request types.
- Made login return a typed token response and centralized credential-rejection
  mapping in controller advice.
- Separated CORS policy, security error responses, JWT claim validation, and shared
  token settings from security configuration.
- Simplified validation error mapping, standardized Java imports and formatting,
  named policy limits, and removed empty Maven scaffold metadata.
- Extended dependency-boundary tests across all modules and gave JWT rejection
  scenarios individual test names for clearer failures.
- Verified a clean package build and 92 Docker-free tests; PostgreSQL verification
  requires an available Docker environment.

### Account HTTP API and authentication

- Added registration, login, own-password change, and administrator account
  listing, role replacement, and enabled-status endpoints under `/api/v1`.
- Added a closed auth module for login orchestration and HS256 bearer tokens with
  a 15-minute lifetime, validated issuer/audience, UUID subjects, and role claims.
- Required Base64-encoded `JWT_SECRET` with at least 32 bytes of key material;
  missing or invalid configuration fails startup, with no application default.
- Added stateless HTTP security, ADMIN checks before request handling, explicit
  CORS origin allowlists, and rejection of cookie/query-string authentication.
- Added shared Problem Details responses for validation, account conflicts,
  authentication, authorization, routing, and unexpected errors. Request DTOs
  reject unknown fields and redact credentials in string representations.
- Covered HTTP administrator bootstrap, generic login failures, expired/tampered
  tokens, missing secrets, and the accepted stale-token authorization window.
- Updated setup, API status, and operator instructions. Task endpoints, the frontend,
  and runtime OpenAPI/Swagger UI remain pending.
- Verified packaging and 79 Docker-free tests. Full verification was attempted;
  15 PostgreSQL-dependent tests could not start because Docker was unavailable.

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
