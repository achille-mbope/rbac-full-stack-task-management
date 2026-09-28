# Documentation

| Guide | Contents |
| --- | --- |
| [Project README](../README.md) | Implementation status, local quick start, technology baseline, and delivery checklist |
| [Backend README](../backend/README.md) | Account/task APIs, authentication setup, manual examples, tests, and administrator recovery |
| [Frontend README](../frontend/README.md) | Angular setup, lazy routes, application layout, API proxy, and frontend checks |
| [API guide](api/README.md) | Request/response rules, task lifecycle, Problem Details, and runtime documentation |
| [OpenAPI contract](api/openapi.json) | Checked-in machine-readable API contract compared with runtime documentation in tests |
| [Production deployment](deployment.md) | Production profile, TLS/proxy trust, request limits, health, diagnostics, and release checks |
| [Local PostgreSQL](local-postgres.md) | Docker Compose startup, persistent data, backend connection settings, and cleanup |
| [Database schema](database.md) | ER diagram, table ownership, constraints, indexes, auditing, migrations, and database profiles |
| [Changelog](../CHANGELOG.md) | Implementation milestones and recorded verification results |

## Architecture decisions

- [ADR-0001: Architecture and repository layout](adr/0001-architecture.md)
- [ADR-0002: Database profiles and migrations](adr/0002-database.md)
- [ADR-0003: Authentication and token lifecycle](adr/0003-authentication.md)
- [ADR-0004: REST API and error contract](adr/0004-api-design.md)
- [ADR-0005: Build tools and versions](adr/0005-build-tool.md)
- [ADR-0006: Security and authorization](adr/0006-security.md)
- [ADR-0007: Module boundaries and verification](adr/0007-modulith.md)

Start with the project README for disposable H2 development. Use the deployment
guide for `prod`; selecting `postgres` alone does not enable production safeguards.
Local PostgreSQL Compose and a packaged production smoke script are available.
The Angular scaffold includes routing, a responsive layout, and a development API
proxy. Authentication and task/account integration remain pending.

## Current handoff

The Angular 21 CSR scaffold now has lazy overview/task/user pages, a responsive
navigation layout, and a development API proxy. Its production build and six tests
passed on 2026-09-29; live proxy forwarding was checked against the H2 backend.
Visual browser verification remains pending. Start with the
[frontend guide](../frontend/README.md) to implement authentication and the first
login-to-task flow.

The backend is ready for frontend integration, with 248 passing tests and a successful
packaged PostgreSQL/TLS smoke run recorded on 2026-09-28. See
[remaining backend work](../backend/README.md#remaining-backend-work) for integration
checks and optional follow-ups. The [public release checklist](deployment.md#public-release-checklist)
tracks deployment-specific validation that local tests do not cover.
