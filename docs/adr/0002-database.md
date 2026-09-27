# ADR-0002: H2 for Quick Development, PostgreSQL for Persistent Environments

**Status:** Accepted  
**Date:** 2026-09-15  
**Updated:** 2026-09-27

## Context

We need disposable local runs and realistic verification of persistence and migrations.

## Decision

- **Quick development:** H2 in memory, Flyway disabled, and
  `spring.jpa.hibernate.ddl-auto=create-drop`. Data is disposable.
- **PostgreSQL development:** PostgreSQL 16 through Docker Compose, Flyway enabled,
  and `spring.jpa.hibernate.ddl-auto=validate`.
- **Production:** PostgreSQL 16 with Flyway and Hibernate validation.
  Local Compose configuration is not a production deployment plan.
- **Schema source of truth:** versioned Flyway SQL migrations in one ordered
  migration directory. Business modules own changes to their tables. Applied
  migrations are immutable; subsequent changes use new migrations.
- **Integration tests:** Testcontainers with PostgreSQL 16 using the same
  migrations and Hibernate validation as the PostgreSQL profile.

## Implementation status (2026-09-27)

V1 creates accounts; V2 adds tasks and administrative audit storage. Tasks reference
accounts by scalar UUID foreign keys without cross-module JPA associations. Audit
rows have no task foreign key so deletion history survives. H2 creates the entity
schema through Hibernate and does not run these migrations. H2 persistence and
rollback tests pass. PostgreSQL 16.15 tests also pass: Flyway applies V1/V2, Hibernate
validates the schema, and integration tests cover constraints, filtering, and audit
rollback. Local Compose remains planned.

## Rationale

H2 gives quick feedback without infrastructure. PostgreSQL tests exercise the
actual SQL dialect and migrations; the PostgreSQL development profile enables
production-like local runs.

## Alternatives

- **PostgreSQL everywhere:** less divergence, but requires a database for every run.
- **H2 everywhere:** insufficient coverage of PostgreSQL behavior and migrations.

## Consequences

- H2 smoke checks do not validate PostgreSQL migrations.
- Persistence changes must pass PostgreSQL integration tests during Maven
  verification; a Docker-compatible container runtime is required.
- Verify clean schema creation and, when altering a released schema, migration
  of existing data.
- Only disposable H2 may automatically create/drop tables. Never use
  `ddl-auto=update` or `create-drop` for persistent environments.
- Production backup, restore, and migration rollout require a deployment plan.
