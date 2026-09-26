# Task Manager

A Spring Boot and Angular learning project focused on modular architecture,
role-based access control, and organizational task management.

See the [changelog](CHANGELOG.md) for the latest project changes.

## Project Status

**Backend implementation started:** the Spring Boot application and Maven Wrapper
are present. The `user` and `auth` modules provide account REST endpoints, persistence,
and JWT authentication. See [backend setup and module API](backend/README.md).
Task management, the frontend, runtime OpenAPI/Swagger UI, and Docker Compose are pending.
The scope below describes the full planned v1.

## Planned Scope

- Registration, login, and password changes.
- Create automatically self-assigned tasks; list, read, update, and delete tasks assigned to you.
- Administrators can create tasks assigned to users or other administrators,
  and list, read, update, or delete all tasks through administrative routes.
- An Angular interface for login, task lists, forms, and filters.
- Administrator operations for listing accounts, assigning roles, and
  enabling or disabling accounts.

All tasks, including user-created tasks, are accessible to their assignee and all
administrators. Personal `/tasks` routes remain assignee-scoped; `/admin/tasks`
provides global administrative access. Reassignment after creation,
general task sharing, and a separate viewer role are outside the accepted
v1 scope. Draft task fields and status transitions are defined in the
[API contract](docs/api/README.md).

### Roles and Permissions

| Operation | USER | ADMIN |
| --- | --- | --- |
| Create self-assigned tasks and manage assigned tasks | Allowed | Allowed |
| Create tasks assigned to users or admins | Denied | Allowed |
| List, read, update, delete all tasks | Denied | Allowed through admin routes |
| Change own password | Allowed | Allowed |
| List accounts, assign roles, enable/disable accounts | Denied | Allowed |

Registration always assigns `USER`. Account permissions are enforced by backend
services and HTTP security. Task assignment enforcement and frontend guards remain
part of the planned task and Angular implementations. See
[ADR-0006](docs/adr/0006-security.md) for the authorization policy.

## Planned Technology Baseline

| Component | Baseline |
| --- | --- |
| Java | 21 LTS |
| Spring Boot | 3.5.x |
| Spring Modulith | 1.4.x |
| Backend build | Maven through the committed Maven Wrapper |
| Frontend | Angular 20 with matching Angular CLI |
| UI library, if used | Angular Material 20 |
| Node.js | 22.x, at least 22.12.0 |
| Frontend package manager | npm with a committed lockfile |
| Disposable development database | H2 in memory |
| Persistent database | PostgreSQL 16 |
| Schema migrations | Flyway |
| Authentication | HS256 JWT bearer tokens |

Backend versions are pinned in its POM and Maven Wrapper. Frontend versions remain
to be pinned. Version selection and maintenance requirements are defined in
[ADR-0005](docs/adr/0005-build-tool.md).

## Architecture

The monorepo contains a single Spring Boot backend deployable. A separate Angular
SPA communicating over REST is planned.

| Location | Purpose | Current state |
| --- | --- | --- |
| `backend/` | Spring Boot modular monolith | Account HTTP API, JWT security, and persistence implemented |
| `frontend/` | Angular SPA | Not scaffolded |
| `docs/adr/` | Accepted architecture decisions | Available |
| `docker-compose.yml` | Local PostgreSQL infrastructure | Planned |

### Backend Modules

| Module | Responsibility | Allowed module dependencies |
| --- | --- | --- |
| `auth` | Login, tokens, HTTP security configuration | `user`, `common` |
| `user` | Accounts, credentials, roles, account administration | `common` |
| `task` | Task lifecycle, assignment, and access enforcement | `user`, `common` |
| `common` | Small domain-neutral shared types and utilities | None |

The `auth`, `user`, and `common` modules are implemented and closed; `task` remains
planned. Public module contracts and DTOs live in module root packages. Internal
code separates application use cases, infrastructure, presentation, and domain
rules where applicable. HTTP request DTOs remain internal to presentation packages.
The planned task module will store stable account IDs without cross-module JPA
associations and validate recipients through the public `user` API.

Spring Modulith verification tests check module cycles, internal-package
access, and explicit dependency allowlists during Maven verification. Skipping
tests skips those checks. `LayerArchitectureTests` also enforce domain, application,
and presentation dependency boundaries across modules. See
[ADR-0007](docs/adr/0007-modulith.md).

Synchronous APIs support interactions requiring an immediate answer, such as
credential verification. Events are optional for independent reactions, with
delivery and consistency requirements defined before introducing them.

The Angular frontend is planned around feature folders, with shared components
and central authentication, guards, and HTTP interceptors.

## Database Profiles

- **Quick development:** disposable H2 with Hibernate schema creation/drop and
  Flyway disabled.
- **PostgreSQL development:** an externally supplied PostgreSQL 16 instance, Flyway
  migrations, and Hibernate schema validation. Local Docker Compose is planned.
- **Production:** PostgreSQL with migrations and validation; local Compose does
  not constitute a production deployment plan.
- **Integration tests:** PostgreSQL Testcontainers running the same migrations.

H2 runs do not validate PostgreSQL behavior. Persistence changes must pass
PostgreSQL integration tests. See [ADR-0002](docs/adr/0002-database.md).

## API and Authentication

The draft [OpenAPI 3.1 contract](docs/api/openapi.json) defines endpoints, request
and response schemas, validation, and errors. See the [contract guide](docs/api/README.md)
for task lifecycle rules and design choices pending implementation.

The implemented account API uses REST and JSON under `/api/v1`, with RFC 9457 Problem
Details errors (`application/problem+json`). OpenAPI will document operations,
schemas, and bearer authentication. Swagger UI is planned at
`/swagger-ui.html` in development.

HTTP security is configured by
[`HttpSecurityConfiguration`](backend/src/main/java/net/mbope/taskmanager/auth/internal/infrastructure/HttpSecurityConfiguration.java)
for servlet web applications. Registration and login are public; `/api/v1/admin/**`
requires ADMIN, and all other routes require authentication. JWT `roles` map to
Spring Security authorities with the `ROLE_` prefix. The filter chain is stateless,
with request caching, CSRF, form login, HTTP Basic, and server-side logout disabled.
See the [backend security guide](backend/README.md#security-and-http-code-ownership)
for CORS handling and internal error dispatches.

Requests authenticate through `Authorization: Bearer <token>`. The v1
authentication tradeoffs are explicit:

- Access tokens expire after 15 minutes. The planned frontend must keep them only in memory.
- Reloading the page or reaching token expiry requires login again; there are
  no refresh tokens.
- Logout clears the local token but does not revoke a copied token.
- Password, role, and account-status changes do not invalidate existing tokens.
  Their previous authority remains until expiry, at most 15 minutes after issuance.
- Disabled accounts cannot obtain new tokens.
- Replacing the active signing key across instances invalidates all existing tokens.

Signing keys and database credentials come from environment variables. Deployed
environments require HTTPS. Login and registration rate limiting is outside v1
and must be addressed before public deployment.

The first administrator is provisioned through the controlled operator procedure in
[the backend guide](backend/README.md#administrator-provisioning-and-recovery):
register through HTTP, promote the account using authorized SQL access, then log in.
No default credentials are shipped.

See [authentication](docs/adr/0003-authentication.md),
[API design](docs/adr/0004-api-design.md), and
[security](docs/adr/0006-security.md) for the complete decisions.

## Getting Started

Install JDK 21 or newer and make it available through `JAVA_HOME` or `PATH`.
Use the committed Maven Wrapper; a separate Maven installation is not required.
The first run needs network access to download Maven and project dependencies.

### Start the backend locally

From the repository root in Windows PowerShell:

```powershell
cd backend
.\setup-local.ps1
.\mvnw.cmd spring-boot:run
```

Run the setup script once per checkout. It creates a signing key in
`backend/.local/application.properties`, which Git ignores, and preserves an
existing file. On subsequent starts, run only `.\mvnw.cmd spring-boot:run` from
`backend/`. An environment `JWT_SECRET` overrides the local key.

The default `h2` profile starts the API at `http://localhost:8080` without Docker
or a separate database. H2 data is lost when the application stops. Press `Ctrl+C`
to stop it. The frontend and Swagger UI are not implemented yet; an unauthenticated
request to `/` returns 401 because that route is protected.

On Unix, run `pwsh ./setup-local.ps1` if PowerShell is installed, then
`./mvnw spring-boot:run`, both from `backend/`. Alternatively, supply an external
`JWT_SECRET` as described in the
[authentication configuration guide](backend/README.md#authentication-configuration).
PostgreSQL startup requires an external signing key and database configuration;
see the [backend guide](backend/README.md#run-and-verify).

### Test registration and login with Postman

With the backend running, follow the
[Postman walkthrough](backend/README.md#test-registration-and-login-with-postman)
to register an account, log in, and send a bearer token. It includes JSON request
bodies, expected status codes, and checks for invalid credentials and permissions.
The default H2 database loses accounts when the backend stops.

### Test the backend

Run these commands from `backend/`. Tests start their own application contexts
and supply a test signing key, so a running backend and local key setup are not required.

For the full test suite and a clean package build, start a Docker-compatible
container runtime first, then run:

```powershell
.\mvnw.cmd clean verify
```

This includes unit, HTTP/JWT, architecture, Spring Modulith, and PostgreSQL
Testcontainers checks. Testcontainers starts its own PostgreSQL database;
Docker-dependent tests fail if Docker is unavailable.

To run the tests that do not require Docker and build the package:

```powershell
.\mvnw.cmd "-Dtest=*,!UserModuleTests,!TaskManagerApplicationTests" clean verify
```

This excludes the two PostgreSQL-dependent test classes and does not verify
PostgreSQL migrations or persistence. Run the full suite with Docker before
treating those behaviors as verified.

On Unix, use `./mvnw` instead of `.\mvnw.cmd`, keeping the test selector quoted.
Test reports are written to `backend/target/surefire-reports/`.

See the [backend guide](backend/README.md) for account endpoints, module contracts,
and administrator provisioning. The frontend remains pending; Node.js/npm and
frontend install/build commands will be needed once it is scaffolded.

## Architecture Decision Records

- [ADR-0001: Architecture and repository layout](docs/adr/0001-architecture.md)
- [ADR-0002: Database profiles and migrations](docs/adr/0002-database.md)
- [ADR-0003: Authentication and token lifecycle](docs/adr/0003-authentication.md)
- [ADR-0004: REST API and error contract](docs/adr/0004-api-design.md)
- [ADR-0005: Build tools and versions](docs/adr/0005-build-tool.md)
- [ADR-0006: Security and authorization](docs/adr/0006-security.md)
- [ADR-0007: Module boundaries and verification](docs/adr/0007-modulith.md)

## V1 Delivery Checklist

- [ ] Backend and frontend scaffolded with pinned tools and dependencies.
- [x] Task fields, validation, and lifecycle specified in the draft API contract.
- [x] Account registration, login, token rejection, and password changes verified through HTTP tests.
- [ ] Frontend authentication, expiry handling, and client-side logout work end to end.
- [ ] User isolation, admin access to all tasks, and role-escalation attempts covered by tests.
- [ ] Self-assignment and admin assignment to users/admins verified.
- [ ] Administrative task edits and deletions recorded in transactional audit records.
- [x] Account administration and documented administrator bootstrap work through HTTP (H2 verified).
- [x] Disabled-account login and the accepted stale-token behavior verified.
- [x] Module verification passes as part of Maven verification.
- [ ] Verify the current revision against PostgreSQL; tests exist, but the latest run was blocked by unavailable Docker.
- [ ] Validation, 401, 403, and ownership-related 404 errors follow the API contract.
- [ ] Frontend login, task list, forms, and filters work with the backend.
- [ ] Local Compose starts PostgreSQL; application startup is documented separately.
- [ ] Development API documentation is available; production access is restricted.
- [ ] Setup and verification instructions are reproducible from a fresh checkout.

Latest local verification: a clean package build and 92 Docker-free tests passed.
This does not replace PostgreSQL migration and persistence verification.

CI configuration is outside v1 scope. Local verification is required; future CI
must run the same checks.

## License

No license has been specified. This repository is a learning project.
