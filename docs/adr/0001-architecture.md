# ADR-0001: Modular Monolith, Separate Angular Frontend, and Monorepo

**Status:** Accepted  
**Date:** 2026-09-15  
**Updated:** 2026-09-23

## Context

A single team is building a task management system where users manage their own
tasks. We need clear responsibilities and straightforward development and deployment.

## Decision

- One Spring Boot deployable organized into `auth`, `user`, `task`, and
  `common` modules with boundaries defined in [ADR-0007](0007-modulith.md).
- A separate Angular SPA communicating with the backend through REST.
- A monorepo with `backend/`, `frontend/`, `docs/`, and a planned
  `docker-compose.yml` for local PostgreSQL.
- Business modules own their entities and repositories. Cross-module access uses
  public service contracts, not another module's repositories or JPA entities.
- Backend authorization follows [ADR-0006](0006-security.md).

## Rationale

A modular monolith combines simple deployment with explicit, testable boundaries.
A monorepo supports coordinated frontend/backend changes in a single commit.

## Alternatives

- **Monolith organized only by technical layers:** fewer initial conventions,
  but less explicit business boundaries.
- **Microservices:** independent scaling and deployment, but additional network,
  data-consistency, and operational complexity.
- **Polyrepo:** independent repository management, but more coordination overhead.

## Consequences

- The backend is released and scaled as a unit.
- Module APIs and frontend/backend contracts require maintenance.
- Service extraction still requires data, transaction, and deployment redesign.
- CI configuration is outside v1 scope. Developers must run local Maven
  verification; future CI must run the same checks and build the frontend.
