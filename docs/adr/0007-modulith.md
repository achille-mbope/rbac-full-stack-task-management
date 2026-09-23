# ADR-0007: Spring Modulith for Boundary Enforcement

**Status:** Accepted  
**Date:** 2026-09-15  
**Updated:** 2026-09-23

## Context

[ADR-0001](0001-architecture.md) chooses a modular monolith. Explicit interfaces
and automated structural checks make the module boundaries verifiable.

## Decision

Use closed modules as direct subpackages of the application root. Each module
has a `package-info.java` with `@ApplicationModule` and explicit
`allowedDependencies`.

| Module | Responsibility | Allowed module dependencies |
| --- | --- | --- |
| `auth` | Login, token issuance/validation, HTTP security | `user`, `common` |
| `user` | Accounts, password hashes, roles, account administration | `common` |
| `task` | Task lifecycle, assignment, and task access enforcement | `user`, `common` |
| `common` | Small domain-neutral shared types/utilities | None |

- For `common`, specify `allowedDependencies = {}`; do not leave it unspecified.
- Expose service contracts and DTOs in module root packages. Keep implementations,
  JPA entities, and repositories in internal subpackages.
- The `user` API provides credential verification and returns an identity/roles
  result without exposing password hashes.
- For admin task assignment, the `user` API also exposes a synchronous account
  lookup returning the stable account ID and enabled status. The `task` module
  uses this public contract to validate recipients; it does not access user repositories.
- Business services obtain the authenticated principal through Spring Security,
  without depending on `auth` internals. Tasks store stable assignee and creator IDs
  without cross-module JPA associations. Assignment is synchronous; events remain deferred.
- The `task` module enforces assignee-scoped personal operations and ADMIN-only
  global operations. Creator IDs are attribution, not access grants. It also owns
  synchronous audit persistence for admin edits/deletions, in the mutation transaction.
- Do not put business entities, repositories, or shared business services in
  `common`. Add shared types only when needed by actual consumers.

### Verification and documentation

- Add `spring-modulith-starter-core` and test-scoped
  `spring-modulith-starter-test`, using [ADR-0005](0005-build-tool.md)'s BOM.
- A discoverable `ModularityTests` calls
  `ApplicationModules.of(TaskManagerApplication.class).verify()`.
- Run it in the normal Maven test lifecycle. `./mvnw verify` must reject
  module cycles, internal-package access, and disallowed dependencies.
  Skipping tests also skips this enforcement.
- Use `@ApplicationModuleTest` where appropriate, explicitly configuring
  dependency inclusion for tests that need collaborating modules.
- Generate module diagrams with Modulith documentation support during verification.
  Regenerate after structural changes; diagrams supplement rather than replace ADRs.

### Communication

Use synchronous public APIs when a caller needs an immediate result, such as
credential verification. Events are optional for independent reactions; v1
does not require asynchronous communication solely to demonstrate modularity.

Before introducing asynchronous events, define transaction boundaries, delivery
and retry behavior, idempotency, and acceptable eventual consistency. An async
listener alone is not a durable delivery guarantee.

## Rationale

Structural checks catch accidental coupling. Explicit allowlists document the
intended graph. Behavioral tests and code review remain necessary.

## Alternatives

- **Conventions only:** less setup but no automated structural feedback.
- **ArchUnit alone:** viable; Modulith additionally supports module tests and diagrams.
- **Microservices:** operational complexity beyond this project's needs.

## Consequences

- Module API changes require reviewing consumers.
- New dependencies require updating this graph and the annotation allowlists.
- CI remains outside v1 scope. Local Maven verification is mandatory; future CI
  must run the same command.
- Structural checks do not catch every runtime dependency or direct SQL access.
  Review and integration tests must also enforce data ownership.

## References

- [Module fundamentals](https://docs.spring.io/spring-modulith/reference/fundamentals.html)
- [Module verification](https://docs.spring.io/spring-modulith/reference/verification.html)
