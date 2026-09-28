# Backend

The backend uses Java 21, Spring Boot 3.5.16, Spring Modulith 1.4.13, and the committed Maven Wrapper.

## Current scope

The `user` module implements account services, persistence, and account HTTP adapters.
The `auth` module implements login, JWT issuance/validation, and stateless HTTP security.
The `common` module provides consistent Problem Details responses, a shared clock,
runtime OpenAPI metadata, production configuration checks, and request diagnostics.
The `task` module now contains its framework-independent domain: immutable assignee
and creator IDs, title/description validation by Unicode code points, optional due
dates, and TODO/IN_PROGRESS/DONE status changes (including reopening). New tasks
default to TODO; all editable fields are validated before an update changes state.
Task application services now implement personal and administrative use cases,
assignment validation, and authorization through ports. JPA persistence, principal/audit
adapters, transactional Spring wiring, and personal/admin HTTP adapters are implemented.
Runtime OpenAPI/Swagger UI, production configuration safeguards, authentication
rate limiting, and health/request diagnostics are implemented. The Angular UI remains pending.
See the [production deployment guide](../docs/deployment.md).

## HTTP endpoints

| Method | Path | Access |
| --- | --- | --- |
| POST | `/api/v1/auth/register` | Public; creates an enabled USER, without issuing a token |
| POST | `/api/v1/auth/login` | Public; returns a 900-second bearer token with `Cache-Control: no-store` |
| PUT | `/api/v1/users/me/password` | Authenticated; returns 204 |
| GET | `/api/v1/admin/users` | ADMIN; paginated account list |
| PUT | `/api/v1/admin/users/{userId}/roles` | ADMIN; replaces roles |
| PUT | `/api/v1/admin/users/{userId}/status` | ADMIN; enables/disables an account |
| POST | `/api/v1/tasks` | Authenticated; creates a self-assigned task and returns 201 with Location |
| GET | `/api/v1/tasks` | Lists tasks assigned to caller, including for ADMIN |
| GET/PATCH/DELETE | `/api/v1/tasks/{taskId}` | Assignee; inaccessible tasks return 404 |
| POST | `/api/v1/admin/tasks` | ADMIN; creates a task assigned to an enabled account |
| GET | `/api/v1/admin/tasks` | ADMIN; lists all tasks with optional assignee filter |
| GET/PATCH/DELETE | `/api/v1/admin/tasks/{taskId}` | ADMIN; manages any task, with audit records for edits/deletions |

JSON request/response shapes follow [the API contract](../docs/api/openapi.json).
Unknown JSON fields are rejected, including roles supplied during registration.
All controlled errors, including authentication and CORS failures, use
`application/problem+json`. Invalid login responses do not reveal account status.

## Runtime OpenAPI and Swagger UI

The backend uses springdoc-openapi 2.8.17 to generate OpenAPI 3.1 from controllers
and DTOs, supplemented with application/domain constraints and Problem Details
metadata. The checked-in [contract](../docs/api/openapi.json) is an independent
test input; it is not served or bundled as the runtime specification.

| Resource | URL |
| --- | --- |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| JSON specification | `http://localhost:8080/v3/api-docs` |
| YAML specification | `http://localhost:8080/v3/api-docs.yaml` |

The local `h2` profile enables documentation by default. Other profiles, including
`postgres`, disable it. Set `API_DOCS_ENABLED=false` to disable it in H2 too.

| Profile / override | Documentation access |
| --- | --- |
| Local `h2`, no override | Public |
| `postgres` or another profile, no override | Disabled |
| Any profile with `API_DOCS_ENABLED=false` | Disabled |
| Non-prod profile with `API_DOCS_ENABLED=true` | Public |
| `prod` | Disabled; attempts to enable documentation properties fail startup |

Setting `API_DOCS_ENABLED=true` explicitly enables **public** documentation in
non-production profiles. In `prod`, this switch cannot enable documentation, and
unsafe overrides of the underlying properties cause startup to fail. Configure this switch rather than
individual springdoc flags. Disabled resources are not registered, and security
also denies their JSON, YAML, configuration, UI, and asset URLs to every role
(anonymous requests receive 401; authenticated requests receive 403).

In Swagger UI, register and log in, copy `accessToken`, click **Authorize**, and
paste the JWT without the `Bearer` prefix. Authorization is held in memory and
is not persisted across page reloads. Registration and login remain public;
protected operations require the token and administrative operations require ADMIN.
Try-it-out requests execute real operations against the running database.

Run documentation checks from `backend/` without Docker:

```powershell
.\mvnw.cmd "-Dtest=OpenApiTests,OpenApiDisabledTests" test
```

`OpenApiTests` compares all 16 generated operations against the contract: effective
paths/methods, operation IDs, parameters, request/response schemas and constraints,
status codes, media types, headers, bearer security, and ADMIN role metadata. It
normalizes references, ordering, integer formats, and descriptive text; nullable
PATCH fields and unknown-property rejection remain part of the comparison. The
generated JSON is saved to `backend/target/openapi.json` for review. Tests also
exercise UI assets/configuration and disabled access; the full Docker suite checks
the disabled default in the PostgreSQL application context.

### Maintaining the API documentation

Controller annotations define operation IDs and success responses; presentation DTO
annotations define wire-schema names. `common/internal/infrastructure/OpenApiConfiguration`
adds domain-enforced constraints, shared errors, headers, and security metadata.
`auth/internal/infrastructure/HttpSecurityConfiguration` enforces documentation access.
Domain and application code remain independent of springdoc.

When an API changes, update the controllers/DTOs, runtime metadata where needed, and
`docs/api/openapi.json` together. Run the focused documentation tests above and review
`target/openapi.json`; resolve differences before running full `clean verify`.
Do not edit the generated file: the next test run overwrites it. The comparison
checks documented shapes; HTTP and persistence tests verify actual behavior.

## Test registration and login with Postman

Start the backend from the repository root in PowerShell (Java 21+ required):

```powershell
cd backend
.\setup-local.ps1
.\mvnw.cmd spring-boot:run
```

Run setup once per checkout, then leave the backend running while sending requests.
The default H2 profile needs no Docker or separate database. These requests use
`http://localhost:8080`; Swagger UI is available at `/swagger-ui.html`. The frontend remains planned.

### Register an account

In Postman, create a request with:

- Method: **POST**
- URL: `http://localhost:8080/api/v1/auth/register`
- **Authorization**: **No Auth**
- **Body**: **raw**, with **JSON** selected (`Content-Type: application/json`)

```json
{
  "email": "alice@example.com",
  "password": "a long original password"
}
```

Click **Send**. Expect **201 Created** with account details and the `USER` role.
Registration does not issue a token. Send only `email` and `password`; extra fields
such as `roles` are rejected. New passwords require at least 15 Unicode code points
and at most 72 UTF-8 bytes. Reusing an existing email returns **409 Conflict**.

### Log in

Create a second **POST** request to
`http://localhost:8080/api/v1/auth/login`. Select **No Auth** and **Body > raw > JSON**,
then send the same email and password. Expect **200 OK** with this response shape:

```json
{
  "accessToken": "<your JWT>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

Copy the `accessToken` value. It expires after 15 minutes; log in again for a new
token. There is no refresh-token endpoint.

### Send the token and check permissions

Create a **GET** request to `http://localhost:8080/api/v1/admin/users`.
Under **Authorization**, select **Bearer Token** and paste the token into **Token**
without adding the word `Bearer`. The resulting header is
`Authorization: Bearer <your JWT>`.

A newly registered USER receives **403 Forbidden** on this ADMIN-only endpoint.
This is the expected permission check. Switching to **No Auth** returns
**401 Unauthorized**. An administrator can receive **200 OK**; see
[administrator provisioning](#administrator-provisioning-and-recovery).

| Check | Expected response |
| --- | --- |
| Register a new email with a valid password | 201 Created |
| Register the same email again | 409 Conflict |
| Register with password `short` or an extra `roles` field | 400 Bad Request |
| Log in with valid credentials | 200 OK with a bearer token |
| Log in with an incorrect password | 401 Unauthorized |
| Exceed the login or registration quota | 429 Too Many Requests with Retry-After |
| GET `/api/v1/admin/users` without a token or with an expired token | 401 Unauthorized |
| GET `/api/v1/admin/users` with a valid USER token | 403 Forbidden |

Errors use `application/problem+json`. If Postman cannot connect, confirm the
backend has finished starting and is still running on port 8080. Restarting the
default H2 backend deletes accounts: register again before logging in.

Rate limits also apply during manual H2 development: the defaults allow 5 registration
and 20 login attempts per client IP per 60-second window, subject to process-wide
quotas. Successful and malformed attempts count. If you receive 429, wait for the
number of seconds in `Retry-After` before trying again. See the
[rate-limit settings](../docs/deployment.md#authentication-request-limits).

To run the automated account HTTP/JWT tests from `backend/`:

```powershell
.\mvnw.cmd "-Dtest=UserHttpTests" test
```

These tests supply their own signing key and application context; they need neither
Docker nor a running backend.

## Authentication configuration

For local H2 development, run this one-time setup from `backend/`:

```powershell
.\setup-local.ps1
```

It generates 32 random bytes and saves the Base64-encoded signing key in
`backend/.local/application.properties`, which Git ignores. The script preserves
an existing file.

After setup, start the application with:

```powershell
.\mvnw.cmd spring-boot:run
```

The H2 profile imports that optional file relative to the application's working
directory. An environment `JWT_SECRET` takes precedence. Keep the same local file
to reuse the signing key across restarts; H2 account data is still disposable.
A fresh checkout needs the one-time setup again because the file is not committed.

For PostgreSQL/deployed environments, supply `JWT_SECRET` externally as Base64-encoded,
cryptographically random key material of at least 32 bytes. The local file is imported
only by the H2 profile. Missing, malformed, or short keys still fail startup.
Test resources supply a separate test-only fixture.

Keep production keys in environment/secret management. Reuse the configured key across
instances; replacing it invalidates outstanding tokens. Never log or commit the key.
Tokens use HS256, issuer `task-manager`, audience `task-manager-api`, a UUID subject,
and USER/ADMIN roles. Validation checks the signature, algorithm, issuer, audience,
required claims, and expiration with no expiry grace period.

Only `Authorization: Bearer <token>` authenticates requests. Sessions, form login,
HTTP Basic, cookie authentication, and query-string tokens are not used. Existing tokens
retain their authority after password, role, or enabled-status changes until expiry;
disabled accounts cannot log in again. Logout is client-side token removal.

Set `CORS_ALLOWED_ORIGINS` to a comma-separated list of explicit browser origins.
H2 development defaults to `http://localhost:4200`; other profiles default to no
cross-origin access. Wildcards are rejected. Deployed environments require HTTPS.
Login/registration rate limiting is enabled by default, with separate per-client
and process-wide quotas, HTTP 429 Problem Details, and Retry-After. It cannot be
disabled in `prod`. See the [limits and deployment requirements](../docs/deployment.md).

## Security and HTTP code ownership

| Component | Responsibility |
| --- | --- |
| `auth.internal.presentation.LoginController` | Accept login requests and return typed, non-cacheable token responses |
| `auth.internal.application.LoginService` | Verify credentials through the public user API and request token issuance |
| `auth.internal.presentation.LoginProblemAdvice` | Map rejected credentials to a generic 401 |
| `auth.internal.infrastructure.HttpSecurityConfiguration` | Wire the stateless filter chain and route authorization |
| `auth.internal.infrastructure.CorsPolicy` | Configure allowed origins and preflight handling |
| `auth.internal.infrastructure.AuthenticationRateLimiter`, `AuthenticationRateLimitFilter` | Enforce bounded authentication quotas before parsing/hashing |
| `auth.internal.infrastructure.SecureTransportFilter` | Reject insecure production requests based on container TLS state |
| `common.internal.infrastructure.ProductionConfiguration` | Validate production settings before database initialization |
| `auth.internal.infrastructure.SecurityProblemHandlers` | Write authentication/authorization Problem Details from security filters |
| `auth.internal.infrastructure.JwtConfiguration` | Load the signing key and configure the encoder/decoder |
| `auth.internal.infrastructure.JwtPolicy`, `JwtClaimsValidator`, `JwtTokenIssuer` | Define token settings, validate claims, and issue tokens |
| `user.internal.presentation` | Separate registration, password, and account-administration controllers and their request DTOs |
| `common.internal.presentation` | Strict JSON, shared HTTP validation/errors, generated request IDs, and safe failure diagnostics |
| `task.internal.presentation.PersonalTaskController` | Assignee-scoped task CRUD through `PersonalTasks` |
| `task.internal.presentation.TaskAdministrationController` | Administrative task CRUD through `TaskAdministration` |
| `task.internal.presentation.TaskProblemAdvice` | Map task validation, missing resources, and disabled recipients to Problem Details |
| `task.internal.presentation` request/response DTOs | Creation defaults, PATCH field presence, and HTTP response shapes |

`HttpSecurityConfiguration` activates only for servlet web applications. Its
stateless filter chain disables request caching, so unauthenticated requests are
not saved for replay after login. The JWT authentication converter reads the
`roles` claim and adds the `ROLE_` prefix, mapping ADMIN to `ROLE_ADMIN` for
the route's `hasRole("ADMIN")` check.

The filter chain allows public registration/login paths, requires ADMIN for
`/api/v1/admin/**`, and requires authentication for other routes. Controllers still
determine allowed HTTP methods. Unknown protected paths return 401 without a token
and 404 for an authenticated caller who passes the route's role requirement.
ADMIN authorization runs before request-body handling, then application services
check it again before business validation or target lookup.

The chain disables CSRF because authentication uses bearer headers without ambient
browser credentials. Adding authentication cookies requires revisiting that decision.
CORS runs before bearer authentication so allowed preflight requests need no token.
Its filter is installed only in the security chain, not as a second servlet filter.
Internal error dispatches are allowed so error responses can be rendered; this
does not expose normal protected requests.

## User module API

Public contracts and immutable DTOs live in `net.mbope.taskmanager.user`.
Implementation code lives under `user.internal`:

- `domain`: framework-independent `UserAccount`, immutable `AccountRoles`, and password policy.
- `application`: registration, credentials, lookup, and administration orchestration.
- `application.port`: contracts for account storage, current principal, email normalization, and password hashing.
- `infrastructure`: JPA mapping and storage, Spring Security, BCrypt, Jakarta email validation, and configuration.
- `presentation`: account controllers, request DTOs, and account error mapping.

Public internal types enable collaboration between these packages; Spring Modulith
still prevents other modules from accessing them. Application services own transactions
and depend on ports rather than infrastructure. Domain code has no Spring, JPA, or
HTTP dependencies. Controllers call public user contracts, never persistence ports.
Tests mirror the owning layer; shared module contract tests run against H2 and PostgreSQL.
Project-wide `LayerArchitectureTests` enforce dependency direction for domain,
application, and presentation packages. Controllers handle one use case group each;
security configuration delegates CORS, JWT claim validation, and security errors
to dedicated components.

| Contract | Responsibility |
| --- | --- |
| `AccountRegistration` | Register an enabled USER using email and password only |
| `UserCredentials` | Verify credentials; change the authenticated account's password |
| `AccountLookup` | Return stable ID and enabled status for assignment validation |
| `AccountAdministration` | ADMIN-only paginated listing, role replacement, enabling/disabling |

Email addresses are stripped of surrounding whitespace and lowercased with
`Locale.ROOT`. A named database constraint enforces uniqueness, including concurrent
registration. `RegistrationService` validates input and hashes passwords through ports,
then creates a domain account. `JpaAccountStore` maps domain accounts to JPA entities,
flushes new registrations, and translates only the email uniqueness constraint into
`EmailAlreadyRegisteredException`; unrelated database failures remain failures.
The exception remains a framework-independent business failure in the public API.
All account use cases access persistence through `AccountStore`. Domain role values
enforce the mandatory USER grant before changes reach persistence.

New passwords require 15 Unicode code points and at most 72 UTF-8 bytes.
Passwords are preserved exactly and hashed with BCrypt strength 12. Credential
verification returns an empty optional for unknown, disabled, and incorrect-password
accounts, and performs a hash comparison in all three cases. Account views and
identity results never expose password hashes.

Every account has USER. The database stores the additional ADMIN grant as
`is_admin`; this representation is intentionally limited to the two v1 roles.
Role replacement must retain USER. Account IDs and creation times are immutable.
Updates follow the contract's last-write-wins policy; Hibernate updates only changed
columns so a password update does not overwrite an unrelated role/status change.

Application services check the authenticated principal through the `CurrentAccount` port;
its Spring Security adapter reads the security context.
The auth module sets the principal name to the account UUID and maps
token roles to `ROLE_USER`/`ROLE_ADMIN`. Admin checks precede validation and target
lookup. Password changes have no caller-supplied account ID. Authorization uses the
current principal's authorities, preserving the accepted JWT expiry semantics after
role/status changes.

The user module depends only on `common` for HTTP errors and shared infrastructure.
The auth module consumes public user contracts and common HTTP error support.
Constructor injection, focused interfaces, and separate registration, credential, lookup, and administration services keep these
responsibilities independent.

## Task domain and next steps

The closed `task` module allows dependencies on public `user` and `common` APIs.
Its current implementation consists of:

| Type | Responsibility |
| --- | --- |
| `task.internal.domain.Task` | Task state, creation/restoration, and atomic updates |
| `task.TaskStatus` | TODO, IN_PROGRESS, and DONE |
| `task.TaskValidationException` | Safe field/code/message validation details |

The domain has no Spring, JPA, or HTTP dependencies. Titles must contain a
non-whitespace character and at most 200 Unicode code points; optional descriptions
allow up to 10,000 code points. Supplied text is preserved. Description and due date
may be cleared with null. Past due dates are valid and do not change status.
Every status transition is allowed, including reopening DONE and retaining a status.

The minimal creation factory defaults to TODO with null description and due date;
the full factory accepts explicit editable values. Assignee and creator IDs are
required and immutable. Unsaved tasks have no ID, following the account domain's
persistence-assigned ID convention. Restoration requires an ID. Creation timestamps
are immutable, update timestamps are supplied by the caller, and both use microsecond
precision. Updates validate all values before changing any state.

Application contracts `PersonalTasks` and `TaskAdministration` expose create, get,
list, update, and delete use cases. `PersonalTaskService` derives self-assignment and
creator IDs from `CurrentTaskActor` and uses assignee-scoped storage even for ADMIN.
Missing and inaccessible tasks raise the same `TaskNotFoundException`.
`TaskAdministrationService` checks ADMIN before validation or lookup, validates new
recipients through public `AccountLookup`, and can manage existing tasks without
rechecking recipient status. Creator attribution alone grants no access.

Immutable `TaskDetails`/`TaskPage` records form the read API. `TaskDraft` contains only
editable creation values. `TaskUpdate` uses `FieldChange` to distinguish omission from
explicit null, rejects empty updates in the use case, and preserves immutable attribution.
`TaskQuery` validates pagination and trims the literal title filter. `TaskStore` must
apply scope and all filters before counting/pagination, with the contract's stable ordering.

Task-owned ports are `TaskStore`, `CurrentTaskActor`, and `TaskAudit`. Admin updates
and deletions append immutable audit entries with actor/task IDs, UTC time, action,
and field changes; failures propagate. Transaction annotations declare the intended
boundary. `TaskConfiguration` registers the services as transactional beans, and
`SpringSecurityTaskActor` reads token identity/authorities without querying account
status on each request.

`JpaTaskStore` maps domain objects to internal JPA entities. Queries apply assignee,
status, and case-insensitive literal title filters before counting/pagination. LIKE
wildcards and the escape character are escaped. Updates preserve attribution and
creation time and use Hibernate dynamic updates. Database account references are
scalar UUIDs, with foreign keys in the PostgreSQL migration and no cross-module JPA associations.

`V2__create_tasks_and_audit.sql` adds task/audit tables, constraints, and list indexes.
`JpaTaskAudit` writes JSON field changes as text and flushes in the task mutation's
transaction. Both adapters require an existing transaction. Audit rows have no task
foreign key and survive deletion. There is no public audit retrieval endpoint;
database/operator access must restrict audit reads.

Eight shared persistence contract tests cover storage, query isolation/filtering,
ordering, audit survival, principal validation, and rollback after audit flush.
They pass on H2 and PostgreSQL 16.15. The PostgreSQL subclass adds V2 constraint
checks; all nine task PostgreSQL tests pass. Flyway applies V1/V2 and Hibernate
validates the migrated schema before these integration tests run.

`PersonalTaskController` and `TaskAdministrationController` expose these use cases.
Creation DTOs accept title, description, status (default TODO), and a YYYY-MM-DD due
date; only the admin DTO accepts assigneeId. Creator IDs, task IDs, and timestamps
are server-owned. `UpdateTaskRequest` records field presence: omission preserves a
value, while null clears description/dueDate. Empty patches and null title/status
are rejected. Unknown fields and incompatible JSON types are rejected.

`TaskResponse` and `TaskPageResponse` map public use-case results to HTTP responses.
Creation returns 201 and a Location under the same personal/admin route; deletion
returns 204 with an empty body. `TaskProblemAdvice` maps validation, missing/inaccessible
tasks, missing recipients, and disabled recipients to the documented Problem Details.
Admin authorization runs in the existing security chain before body/path parsing,
and again in application services before lookup. Controllers access public task
contracts without reaching into domain or persistence packages.

### Try the task API

After login, send the bearer token with `Content-Type: application/json`:

```http
POST /api/v1/tasks
Authorization: Bearer <accessToken>
Content-Type: application/json

{"title":"Plan work","description":"Prepare the next release","dueDate":"2026-10-01"}
```

Follow the response's Location to read the task. To complete it or clear its due date,
send `PATCH` to that same URL with `{"status":"DONE","dueDate":null}`. Use
`GET /api/v1/tasks?page=0&size=20&status=TODO&q=plan` to filter your tasks. Administrators
use `POST /api/v1/admin/tasks` with an additional `assigneeId`; personal routes stay
assignee-scoped even for administrators. Admin lists accept an optional `assigneeId`.
Title search is a case-insensitive literal substring, so URL-encode query values.

| Request outcome | HTTP response |
| --- | --- |
| Create a personal or assigned task | 201 with task body and Location |
| Read, list, or update tasks | 200 with task or page body |
| Delete a task | 204 with no body |
| Invalid fields, empty PATCH, or immutable fields supplied | 400 validation Problem Details |
| Missing or invalid bearer token | 401 with a bearer challenge |
| Non-admin calls an admin task route | 403 before request-body parsing or lookup |
| Missing task or another assignee's task on personal routes | Identical 404 details |
| Missing assignment recipient | 404 |
| Disabled assignment recipient | 409 with `assignee-disabled` problem type |

Run only the task HTTP checks from `backend/` with
`.\mvnw.cmd "-Dtest=TaskHttpTests" test`. These checks use signed JWTs, the real
security filter chain, and disposable H2 storage; no separate running server is needed.

## Production startup and operations

Deploy with `SPRING_PROFILES_ACTIVE=prod`; it includes the PostgreSQL profile and
validates configuration before database initialization. Supply database credentials
and a production JWT signing key, configure direct TLS or explicitly trusted native
proxy processing, and use HTTPS CORS origins. The local default remains H2.

Only minimal health/liveness/readiness endpoints are public. Other management
endpoints are denied even to ADMIN. Responses carry generated `X-Request-ID` values;
unexpected MVC failures log correlation and stack locations without secret messages.

The [deployment runbook](../docs/deployment.md) documents startup, proxy trust,
rate-limit tuning and scaling limits, health checks, diagnostics, and release checks.

## Run and verify

From `backend/`, with Java 21+ and Docker running:

```powershell
.\mvnw.cmd verify
```

On Unix, use `./mvnw verify`. Verification includes PostgreSQL 16 Testcontainers, HTTP/JWT tests,
Flyway migrations with Hibernate schema validation, shared account contract tests on H2,
password/email unit tests, authorization tests, and Spring Modulith verification.
Docker-dependent tests fail if Docker is unavailable; they are not silently skipped.
Module documentation is generated under `target/spring-modulith-docs/`.

For a clean package build with all tests that do not require Docker:

```powershell
.\mvnw.cmd "-Dtest=*,!UserModuleTests,!TaskManagerApplicationTests,!PostgresTaskPersistenceTests,!ProductionHttpTests,!ProductionTrustedProxyTests" clean verify
```

The five excluded classes require PostgreSQL Testcontainers. The Docker-free command
does not verify PostgreSQL behavior; use the full `verify` command with Docker for
that coverage. Tests supply their own signing-key fixture; application startup
requires either the H2 local setup or an external `JWT_SECRET`.

For focused task HTTP, persistence, application, domain, and architecture verification:

```powershell
.\mvnw.cmd "-Dtest=TaskHttpTests,H2TaskPersistenceTests,TaskServiceTests,TaskTests,LayerArchitectureTests,ModularityTests" test
```

On 2026-09-28, `.\mvnw.cmd clean verify` passed all 248 tests with no failures,
errors, or skips and packaged the executable JAR. The run used Maven 3.9.16,
JDK 25.0.1 with Java 21 release compilation, Docker 29.8.0, and PostgreSQL 16.15
from `postgres:16-alpine`.

| Verification scope | Latest result (2026-09-28) |
| --- | --- |
| Full clean verification and executable JAR packaging | Passed; 248 tests |
| Docker-free regression tests | 218 passed within the full run |
| PostgreSQL-backed tests | 30 passed: 9 task, 14 account, 2 application/default-security, 5 production HTTP/proxy |
| Production configuration rejection tests | 26 passed |
| Rate limiter, operational HTTP, and diagnostic tests | 10 passed |
| Production-profile PostgreSQL and real-server tests | 5 passed |
| Packaged JAR smoke test with PostgreSQL and verified TLS | Passed via `smoke-prod.ps1` |
| OpenAPI contract/UI and H2 documentation access tests | 10 passed |
| Documentation disabled by default outside H2 | Passed in the PostgreSQL application tests |
| Task HTTP tests with signed JWTs and H2 | 44 passed |
| Task H2 persistence and transaction tests | 8 passed |

For only the task PostgreSQL checks:

```powershell
.\mvnw.cmd "-Dtest=PostgresTaskPersistenceTests" test
```

The PostgreSQL tests apply Flyway V1/V2 to a disposable database and validate the
result with Hibernate. They verify invalid status/oversized title/orphan assignee
constraints, Unicode round trips, scoped filtering with literal SQL wildcard characters,
filtered counts and pagination, deterministic ordering, and assignment validation.
They also verify that audit records survive deletion and that a failure after an
audit flush rolls back both task and audit writes.

Earlier Docker and Maven-cache failures came from restricted-process access. Docker
was running; executing Maven with access to the Docker pipe and dependency cache
resolved both failures without production-code changes. H2 tests remain useful for
quick checks but do not replace the PostgreSQL suite.

The account tests were introduced before their implementations. The development-profile
duplicate-email test also reproduced an H2/PostgreSQL constraint-name difference
before the exception translation was corrected.

After the one-time local setup above, start the disposable H2 application:

```powershell
.\mvnw.cmd spring-boot:run
```

The default `h2` profile uses an in-memory database and Hibernate create/drop, with
Flyway disabled. Data is lost on shutdown. For a persistent PostgreSQL 16 instance,
set `SPRING_PROFILES_ACTIVE=postgres`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET`
in the environment, then use the same startup command. This profile runs Flyway
and validates the schema; it never creates/drops it through Hibernate.
The root Compose file supplies persistent local PostgreSQL. Follow the
[local PostgreSQL guide](../docs/local-postgres.md) for database startup, matching
backend settings, and stopping the container without losing data.

The `postgres` profile alone selects persistence settings; production safeguards
require `SPRING_PROFILES_ACTIVE=prod`. See the [deployment runbook](../docs/deployment.md).

### Test configuration and packaged verification

Tests load `src/main/resources/application.properties` directly. Test-only overrides
live in `src/test/resources/application-test.properties`; there is no shadow copy
of the base file on the test classpath. PostgreSQL service-connection tests activate
`test`; H2 tests activate `test,h2`; production tests activate `test,prod` so production
settings retain mandatory rate limiting. Operational tests explicitly enable quotas.
The signing-key fixture is a test resource and is not packaged in the JAR.

After building, run `.\smoke-prod.ps1` to exercise the actual executable JAR with
isolated PostgreSQL and certificate-verified TLS. Requires Docker Compose v2 and
`java`, JDK `keytool`, and `curl.exe` on `PATH`. See the
[smoke-test guide](../docs/deployment.md#packaged-production-smoke-test) for checks
and automatic cleanup.

## Remaining backend work

The documented v1 account and task APIs, persistence, authorization, production
safeguards, and operational diagnostics are implemented. Current verification is
248 passing Maven tests plus the packaged PostgreSQL/TLS smoke test. Frontend
integration is the next project milestone; it does not require expanding the API first.

During integration, verify browser CORS, bearer-token handling, expiry and client-side
logout, validation/permission errors, and rate-limit feedback. These end-to-end flows
remain unverified until the frontend exists.

Before public release, complete the [deployment checklist](../docs/deployment.md#public-release-checklist)
against the real environment. Local smoke testing does not verify deployed DNS,
proxy rules, secret delivery, backup recovery, or alert routing.

Optional follow-up work includes CI running the existing verification commands and
a review of concurrent task edits. These are not recorded as completed. Refresh
tokens, immediate token revocation, and password recovery would require separate
scope and contract decisions; the existing token-lifecycle limitations still apply.

## Administrator provisioning and recovery

No default administrator or credentials are shipped. Register the intended operator
with `POST /api/v1/auth/register` using email and password, then use an authorized
PostgreSQL operator connection to promote that existing account. The same operation
recovers a demoted or disabled administrator:

```sql
-- In psql, prompt for the existing account's email (not its password).
\prompt 'Administrator account email: ' admin_email
BEGIN;
UPDATE user_accounts
SET is_admin = TRUE, enabled = TRUE, updated_at = CURRENT_TIMESTAMP
WHERE email = lower(btrim(:'admin_email'))
RETURNING id, email;
COMMIT;
```

Confirm that exactly the intended account was returned. No returned row means no
account was changed. Sign in with `POST /api/v1/auth/login` to obtain a token with
the new authorities. This procedure does not reset or expose credentials. HTTP tests
cover registration, operator promotion, administrator login, and account management.
