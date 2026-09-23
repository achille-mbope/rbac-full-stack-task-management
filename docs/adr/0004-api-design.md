# ADR-0004: REST API with RFC 9457 Problem Details

**Status:** Accepted  
**Date:** 2026-09-15  
**Updated:** 2026-09-23

## Context

The frontend needs predictable resource operations and a consistent error contract.

## Decision

- REST with JSON and resource URLs under `/api/v1`.
- RFC 9457 Problem Details, superseding RFC 7807, with
  `Content-Type: application/problem+json`.
- Include `type`, `title`, `status`, `detail`, and `instance`. Use stable
  problem-type URIs and safe occurrence-specific details; body status matches HTTP status.
- Validation failures return 400 with an `errors` array containing `field`,
  `code`, and `message`; never echo rejected secrets.
- Missing, invalid, or expired credentials return 401. Bearer-protected resources
  include an appropriate `WWW-Authenticate` header.
- Insufficient role returns 403. A task belonging to another user returns 404,
  like a nonexistent task, as required by [ADR-0006](0006-security.md).
- Document success/error schemas and bearer authentication using OpenAPI 3.1
  with a Spring Boot-compatible springdoc-openapi release and Swagger UI.

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
- Add `springdoc-openapi-starter-webmvc-ui`. Expose `/swagger-ui.html` in
  development and disable or restrict documentation endpoints in production.
- Verify validation, 401, 403, and ownership-related 404 responses.

## References

- [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html)
