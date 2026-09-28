# ADR-0004: REST API with RFC 9457 Problem Details

**Status:** Accepted  
**Date:** 2026-09-15  
**Updated:** 2026-09-27

## Context

The frontend needs predictable resource operations and a consistent error contract.

## Decision

- REST with JSON and resource URLs under `/api/v1`.
- `POST /tasks` creates an automatically self-assigned task for any authenticated
  user, including admins. Reject request-supplied assignee or creator IDs.
- Personal `GET /tasks` and `GET/PATCH/DELETE /tasks/{taskId}` are assignee-scoped.
- `POST /admin/tasks` accepts an explicit enabled USER or ADMIN assignee.
  `GET /admin/tasks` lists all tasks with pagination and optional assigneeId,
  status, and title-search filters. `GET/PATCH/DELETE /admin/tasks/{taskId}`
  allow administrators to manage any task, regardless of creator or assignee.
- All admin task routes require ADMIN before resource lookup. Task updates cannot
  change assignee or creator IDs. Record admin edits and deletions as specified
  in [ADR-0006](0006-security.md). The [OpenAPI contract](../api/openapi.json)
  defines request/response schemas and error cases.
- RFC 9457 Problem Details, superseding RFC 7807, with
  `Content-Type: application/problem+json`.
- Include `type`, `title`, `status`, `detail`, and `instance`. Use stable
  problem-type URIs and safe occurrence-specific details; body status matches HTTP status.
- Validation failures return 400 with an `errors` array containing `field`,
  `code`, and `message`; never echo rejected secrets.
- Missing, invalid, or expired credentials return 401. Bearer-protected resources
  include an appropriate `WWW-Authenticate` header.
- Insufficient role returns 403. A task inaccessible to the caller returns 404,
  like a nonexistent task, as required by [ADR-0006](0006-security.md).
- Document success/error schemas and bearer authentication using OpenAPI 3.1
  with a Spring Boot-compatible springdoc-openapi release and Swagger UI.

## Implementation status (2026-09-27)

Account endpoints and Problem Details handling are implemented. Task domain and
application contracts support CRUD, assignment/access rules, field-presence-aware
updates, and query validation. Personal/admin task HTTP controllers, request/response
DTOs, and exception-to-response mapping now implement the OpenAPI contract.
Runtime OpenAPI 3.1 and Swagger UI are implemented with springdoc-openapi 2.8.17.
Generated operations, wire schemas, errors, headers, and security are compared with
the independent checked-in contract during Maven tests. See the
[API implementation notes](../api/README.md#implementation-verification).

## Rationale

REST fits task CRUD operations. Problem Details supports consistent frontend
handling without depending on exception text. OpenAPI makes contracts reviewable.

## Alternatives

- **GraphQL:** unnecessary complexity for the initial read patterns.
- **gRPC:** additional browser integration without a requirement for it.
- **Custom JSON RPC/errors:** more conventions to define and maintain.

## Consequences

- All application-controlled API errors follow the contract, including Spring
  Security filter errors and MVC exception-handler responses.
- Frontend code must tolerate infrastructure errors outside this contract.
- Never expose stack traces, SQL, credentials, or internal exception messages.
- Use `springdoc-openapi-starter-webmvc-ui`. The local H2 profile exposes
  `/swagger-ui.html`, `/v3/api-docs`, and `/v3/api-docs.yaml` publicly. Other
  profiles disable them by default. `API_DOCS_ENABLED` controls generation and
  security: false denies all documentation routes to every role; true explicitly
  makes documentation public. Keep it false on deployed hosts. Swagger UI does
  not persist bearer authorization across reloads.
- Verify validation, 401, 403, and ownership-related 404 responses.

## References

- [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html)
