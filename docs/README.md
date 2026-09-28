# Documentation

| Guide | Contents |
| --- | --- |
| [Project README](../README.md) | Implementation status, local quick start, technology baseline, and delivery checklist |
| [Backend README](../backend/README.md) | Account/task APIs, authentication setup, manual examples, tests, and administrator recovery |
| [API guide](api/README.md) | Request/response rules, task lifecycle, Problem Details, and runtime documentation |
| [OpenAPI contract](api/openapi.json) | Checked-in machine-readable API contract compared with runtime documentation in tests |
| [Production deployment](deployment.md) | Production profile, TLS/proxy trust, request limits, health, diagnostics, and release checks |
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
The Angular frontend and local Docker Compose configuration remain planned.
