# ADR-0001: Modular Monolith, Separate Angular Frontend, and Monorepo

**Status:** Accepted  
**Date:** 2026-09-15  
**Updated:** 2026-09-29

## Context

A single team is building an organizational task management system. Users create
self-assigned tasks and manage tasks assigned to them. Administrators assign new
tasks and supervise all tasks, including those created by users. We need clear
responsibilities and straightforward development and deployment.

## Decision

- One Spring Boot deployable organized into `auth`, `user`, `task`, and
  `common` modules with boundaries defined in [ADR-0007](0007-modulith.md).
- A separate client-rendered Angular SPA communicating with the backend through REST.
  SSR and build-time prerendering are not enabled for the authenticated task workspace.
- A monorepo with `backend/`, `frontend/`, `docs/`, and a
  `docker-compose.yml` for local PostgreSQL.
- Business modules own their entities and repositories. Cross-module access uses
  public service contracts, not another module's repositories or JPA entities.
- Backend authorization follows [ADR-0006](0006-security.md).

## Rationale

A modular monolith combines simple deployment with explicit, testable boundaries.
A monorepo supports coordinated frontend/backend changes in a single commit.
Client-side rendering fits the interactive, private workspace and the
in-memory bearer-token model. Public content with search-indexing requirements
would be a separate reason to revisit rendering strategy.

## Alternatives

- **Monolith organized only by technical layers:** fewer initial conventions,
  but less explicit business boundaries.
- **Microservices:** independent scaling and deployment, but additional network,
  data-consistency, and operational complexity.
- **Polyrepo:** independent repository management, but more coordination overhead.

## Consequences

- The backend is released and scaled as a unit.
- Frontend builds are static assets. Production hosting must route `/api/**` to
  the backend before applying an `index.html` fallback for frontend routes.
- The Angular development proxy is local tooling, not production infrastructure.
- Module APIs and frontend/backend contracts require maintenance.
- Service extraction still requires data, transaction, and deployment redesign.
- CI configuration is outside v1 scope. Developers must run local Maven
  verification; future CI must run the same checks and build the frontend.
