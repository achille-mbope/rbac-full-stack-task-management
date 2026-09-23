# Task Manager

A Spring Boot and Angular learning project focused on modular architecture,
role-based access control, and private task management.

## Project Status

**Design stage:** the repository contains architecture decisions and application
directories. Backend and frontend code, build files, tests, and Docker Compose
configuration have not been scaffolded. Features below describe the planned v1,
not an application that can currently be run.

## Planned Scope

- Registration, login, and password changes.
- Create, list, read, update, and delete tasks owned by the authenticated user.
- An Angular interface for login, task lists, forms, and filters.
- Administrator operations for listing accounts, assigning roles, and
  enabling or disabling accounts.

Tasks remain private to their owner, including from administrators. Task
assignment, shared tasks, and a separate viewer role are outside the accepted
v1 scope. Detailed task fields and status transitions remain to be specified.

### Roles and Permissions

| Operation | USER | ADMIN |
| --- | --- | --- |
| Create and manage own tasks | Allowed | Allowed |
| Access another user's tasks | Denied | Denied |
| Change own password | Allowed | Allowed |
| List accounts, assign roles, enable/disable accounts | Denied | Allowed |

Registration always assigns `USER`. Backend services enforce permissions and
task ownership; frontend guards only control navigation. See
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

Exact tool and dependency versions will be pinned when scaffolding. Version
selection and maintenance requirements are defined in
[ADR-0005](docs/adr/0005-build-tool.md).

## Architecture

The monorepo will contain a single Spring Boot backend deployable and a separate
Angular SPA communicating over REST.

| Location | Purpose | Current state |
| --- | --- | --- |
| `backend/` | Spring Boot modular monolith | Not scaffolded |
| `frontend/` | Angular SPA | Not scaffolded |
| `docs/adr/` | Accepted architecture decisions | Available |
| `docker-compose.yml` | Local PostgreSQL infrastructure | Planned |

### Backend Modules

| Module | Responsibility | Allowed module dependencies |
| --- | --- | --- |
| `auth` | Login, tokens, HTTP security configuration | `user`, `common` |
| `user` | Accounts, credentials, roles, account administration | `common` |
| `task` | Task lifecycle and ownership enforcement | `common` |
| `common` | Small domain-neutral shared types and utilities | None |

All modules are closed. Public service contracts and DTOs belong in module root
packages; implementations, entities, and repositories remain internal. Tasks
store a stable owner ID without cross-module JPA associations.

Spring Modulith verification tests will check module cycles, internal-package
access, and explicit dependency allowlists during Maven verification. Skipping
tests skips those checks. See [ADR-0007](docs/adr/0007-modulith.md).

Synchronous APIs support interactions requiring an immediate answer, such as
credential verification. Events are optional for independent reactions, with
delivery and consistency requirements defined before introducing them.

The Angular frontend is planned around feature folders, with shared components
and central authentication, guards, and HTTP interceptors.

## Database Profiles

- **Quick development:** disposable H2 with Hibernate schema creation/drop and
  Flyway disabled.
- **PostgreSQL development:** PostgreSQL 16 through local Docker Compose, Flyway
  migrations, and Hibernate schema validation.
- **Production:** PostgreSQL with migrations and validation; local Compose does
  not constitute a production deployment plan.
- **Integration tests:** PostgreSQL Testcontainers running the same migrations.

H2 runs do not validate PostgreSQL behavior. Persistence changes must pass
PostgreSQL integration tests. See [ADR-0002](docs/adr/0002-database.md).

## API and Authentication

The planned API uses REST and JSON under `/api/v1`, with RFC 9457 Problem
Details errors (`application/problem+json`). OpenAPI will document operations,
schemas, and bearer authentication. Swagger UI is planned at
`/swagger-ui.html` in development.

Requests authenticate through `Authorization: Bearer <token>`. The v1
authentication tradeoffs are explicit:

- Access tokens expire after 15 minutes and are kept only in frontend memory.
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

The first administrator will be provisioned through a controlled operator
procedure, with no default credentials. That procedure has not yet been
implemented; reproducible bootstrap instructions are a delivery requirement.

See [authentication](docs/adr/0003-authentication.md),
[API design](docs/adr/0004-api-design.md), and
[security](docs/adr/0006-security.md) for the complete decisions.

## Getting Started

There is no runnable application yet. Start with the architecture decisions below.

Once scaffolded, development will require Java 21, the selected Node.js/npm
versions, and a Docker-compatible container runtime for PostgreSQL integration
tests. The repository will provide Maven Wrapper scripts and a frontend lockfile.

The planned verification commands are:

| Directory | Command | Purpose |
| --- | --- | --- |
| `backend/` | `./mvnw verify` (Unix) or `.\mvnw.cmd verify` (Windows) | Backend, module, and PostgreSQL checks |
| `frontend/` | `npm ci` | Install locked dependencies |
| `frontend/` | `npm run build` | Build the frontend |

These commands become usable after the corresponding build files are added.
Startup commands, environment examples, administrator bootstrap, and a
non-interactive frontend test command will be documented alongside implementation.

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
- [ ] Task fields, validation, and lifecycle specified in the API contract.
- [ ] Registration, login, expiry, logout, and password changes work end to end.
- [ ] Ownership isolation and role-escalation attempts covered by tests.
- [ ] Account administration and documented administrator bootstrap work.
- [ ] Disabled-account login and the accepted stale-token behavior verified.
- [ ] Module verification passes as part of Maven verification.
- [ ] PostgreSQL migrations and persistence behavior pass integration tests.
- [ ] Validation, 401, 403, and ownership-related 404 errors follow the API contract.
- [ ] Frontend login, task list, forms, and filters work with the backend.
- [ ] Local Compose starts PostgreSQL; application startup is documented separately.
- [ ] Development API documentation is available; production access is restricted.
- [ ] Setup and verification instructions are reproducible from a fresh checkout.

CI configuration is outside v1 scope. Local verification is required; future CI
must run the same checks.

## License

No license has been specified. This repository is a learning project.
