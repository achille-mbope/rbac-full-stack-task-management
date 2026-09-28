# API Contract

[openapi.json](openapi.json) is the OpenAPI 3.1 contract for the planned v1 API.
Its version is `0.1.0-draft`. Account registration, login, password changes, and
administrator account operations are implemented in the backend. The task domain
implements field validation, immutable attribution, and status changes. Application
use cases implement assignment, access rules, and partial updates through ports.
Task persistence and runtime wiring are implemented with H2 integration coverage.
Task HTTP operations and runtime-generated OpenAPI/Swagger UI are implemented.
The runtime specification is compared with this independent contract by `OpenApiTests`.
Import it into an OpenAPI 3.1-compatible viewer or client generator.

For manual testing of the implemented authentication endpoints, follow the
[Postman registration and login walkthrough](../../backend/README.md#test-registration-and-login-with-postman).
It covers local startup, JSON bodies, bearer tokens, and expected success and error
responses. For task requests, see the [task API walkthrough](../../backend/README.md#try-the-task-api).

| Task implementation layer | Current status |
| --- | --- |
| Domain and application use cases | Implemented; domain and in-memory application tests pass |
| JPA storage, principal adapter, and transactional audit wiring | Implemented; H2 and PostgreSQL integration tests pass |
| PostgreSQL migration and persistence verification | Nine task tests pass against PostgreSQL 16.15, including V2 constraints and audit rollback |
| HTTP controllers, JSON update handling, and error mapping | Implemented for personal and admin routes |
| Runtime OpenAPI and Swagger UI | Available in local H2 development; disabled by default elsewhere |

The HTTP implementation uses separate personal/admin creation DTOs. Personal creation
never accepts attribution fields; admin creation accepts only `assigneeId` in addition
to editable task fields. PATCH records property presence, so omitted values are retained
and explicit null clears only description or dueDate. Due dates use YYYY-MM-DD strings.
Both task and paginated responses have dedicated HTTP DTOs. See
[TaskHttpTests](../../backend/src/test/java/net/mbope/taskmanager/task/TaskHttpTests.java)
for executable request/response examples and authorization checks.

## Runtime documentation and contract comparison

See the [runtime documentation guide](../../backend/README.md#runtime-openapi-and-swagger-ui)
for URLs, bearer-token entry, availability controls, and focused test commands.
`API_DOCS_ENABLED=true` makes documentation public; leave it disabled on deployed hosts.

The generated document uses server `/` and full `/api/v1/...` paths; this contract
uses server `/api/v1` and relative paths. Tests compare effective URLs, expand
references, and ignore descriptive text, ordering, redundant `required: false`,
integer formats, and explicit null defaults. They retain request/response property
names, requiredness, nullability, validation constraints, error media types, response
headers, security, and ADMIN role metadata. All 16 operations match. PATCH schemas
describe wire fields rather than the internal field-presence wrapper. Runtime DTO
schema names are aligned with the contract. `backend/target/openapi.json` is the
generated review artifact; the static contract is copied only to test resources.

For intentional API changes, update this contract and runtime declarations together,
then run `OpenApiTests` and `OpenApiDisabledTests` as described in the backend guide.
Keep behavior tests for validation, authorization, and persistence: matching OpenAPI
documents alone does not prove the implementation follows the documented behavior.

## Scope and design choices

The contract follows [ADR-0004](../adr/0004-api-design.md),
[authentication](../adr/0003-authentication.md), and
[authorization](../adr/0006-security.md). The following details fill gaps in the
ADRs and are draft implementation choices:

- Accounts use a unique email address as their login identifier. Trim email
  whitespace and lowercase for storage and lookup. Email changes are outside v1.
  Duplicate registration returns 409; this reveals email registration status.
  Login failures remain identical for unknown, disabled, and incorrect-password accounts.
- IDs are server-generated UUIDs; timestamps are server-generated UTC instants.
  Creation sets createdAt and updatedAt to the same instant. Successful updates
  set updatedAt to the current instant; createdAt is immutable.
- Every account has USER; administrators additionally have ADMIN. Registration
  creates an enabled account with USER only and does not issue a token.
- Tasks contain a required title (1–200 characters, not whitespace-only), optional
  description (up to 10,000 characters), status, and optional calendar due date.
  Character limits count Unicode code points. Description and dueDate default to
  null; status defaults to TODO. Past due dates are allowed.
- Statuses are TODO, IN_PROGRESS, and DONE. Any transition is allowed, including
  reopening DONE. No automatic transition occurs when a due date passes.
- Task PATCH uses application/json with partial-update semantics. Omitted values
  remain unchanged; null clears description or dueDate. An empty object is invalid.
  IDs, assignment, and timestamps are never writable through updates. Deletion is permanent.
- Administrators create assigned tasks through POST /admin/tasks, providing a
  required assigneeId. Recipients must exist and be enabled, and may have USER or
  ADMIN roles (including the caller). Check ADMIN before recipient lookup; missing
  recipients return 404 and disabled recipients return 409. Ordinary POST /tasks
  assigns the task to the caller and rejects client-supplied attribution fields.
- Task responses include immutable assigneeId and createdById. Any authenticated
  user, including ADMIN, creates self-assigned tasks through POST /tasks: the backend
  sets both fields to the token subject and rejects attempts to override them.
- Personal /tasks routes list and manage only tasks assigned to the caller, including
  for ADMIN. Administrative /admin/tasks routes allow any ADMIN to list, view, edit,
  and delete every task, including tasks created by users or other admins and tasks
  assigned to disabled accounts. All tasks are organizational work; none are hidden
  from admins. createdById is attribution only, not an access grant.
- Removing ADMIN ends global access when previously issued ADMIN tokens expire.
  Existing assignments survive account disabling. Reassignment after creation is
  outside v1. Assignment is synchronous; events and notifications remain deferred.
- Admin task edits and deletions create synchronous audit records in the same
  transaction as the mutation. Record actor ID, task ID, UTC timestamp, action, and
  changed fields with before/after values; deletion records survive task deletion.
  Restrict audit access to authorized operators and never record tokens or credentials.
  There is no public audit retrieval endpoint in v1.
- Lists use zero-based page and size (default 20, maximum 100), sorted by createdAt
  descending then id ascending. Beyond the last page returns empty items. Empty
  results have totalElements and totalPages equal to zero. Task status and title
  substring filters are combined with AND; totals are calculated after access checks
  and filtering. The admin list additionally accepts an assigneeId filter; all
  filters combine with AND. A valid assigneeId with no matching tasks returns an
  empty page. Pagination is not a snapshot across concurrent writes.
- Updates use last-write-wins semantics. Optimistic concurrency is outside this draft.
- Administrators can demote or disable themselves and the last administrator.
  There is no last-admin safeguard in this draft. Controlled operator provisioning
  and recovery must be documented during implementation; there is no public bootstrap API.

## Operations

All paths below are relative to `/api/v1`. Protected operations require a bearer
JWT. ADMIN checks use token roles and run before looking up a target account or task.

| Method | Path | Access | Success |
| --- | --- | --- | --- |
| POST | `/auth/register` | Public | 201 account |
| POST | `/auth/login` | Public | 200 token |
| PUT | `/users/me/password` | Authenticated | 204 |
| GET | `/tasks` | Only tasks assigned to caller, including for ADMIN | 200 page |
| POST | `/tasks` | Authenticated; automatically self-assigned | 201 task and Location |
| POST | `/admin/tasks` | ADMIN; enabled USER or ADMIN recipient | 201 task and Location |
| GET | `/tasks/{taskId}` | Assignee | 200 task |
| PATCH | `/tasks/{taskId}` | Assignee | 200 task |
| DELETE | `/tasks/{taskId}` | Assignee | 204 |
| GET | `/admin/tasks` | ADMIN; all tasks | 200 page |
| GET | `/admin/tasks/{taskId}` | ADMIN; any task | 200 task |
| PATCH | `/admin/tasks/{taskId}` | ADMIN; any task | 200 task |
| DELETE | `/admin/tasks/{taskId}` | ADMIN; any task | 204 |
| GET | `/admin/users` | ADMIN | 200 page |
| PUT | `/admin/users/{userId}/roles` | ADMIN | 200 account |
| PUT | `/admin/users/{userId}/status` | ADMIN | 200 account |

Task creation returns Location pointing to `/api/v1/tasks/{taskId}` for ordinary
creation and `/api/v1/admin/tasks/{taskId}` for admin assignment, so the creator can
follow the returned URL even when the task is assigned to someone else.

There is no refresh or logout endpoint. Logout clears the frontend's in-memory
access token. Reloading or token expiry requires login again. Tokens expire after
900 seconds; role, password, and account-status changes do not revoke them.
Disabled accounts cannot obtain new tokens, but existing tokens retain their
previous authority until expiry. Replacing the active signing key across instances
invalidates outstanding tokens. Token responses must use Cache-Control: no-store.

## Validation and errors

Request bodies must be JSON objects and reject unknown properties, including
client-supplied roles during registration and creator/owner IDs during task operations.
Only the admin creation schema accepts assigneeId; task updates reject it.
Invalid JSON, IDs, query parameters, missing required fields, and schema violations
return 400 with field errors. Unsupported body media types return 415.
Password validation must enforce both the 15-code-point minimum for new passwords
and the 72-byte UTF-8 maximum. Do not trim or normalize passwords. Login and current
password inputs are not subject to the new-password minimum. A wrong current
password returns 400 with code incorrect_password, never the rejected password.

All application-controlled errors, including security-filter failures, use
application/problem+json with type, title, status, detail, and instance. Body status
must match the HTTP status. Validation errors also include a nonempty errors array
of field/code/message objects. Unknown JSON fields use their field name; malformed
whole requests use `$`. Do not echo rejected values. Clients must tolerate additional
problem extensions and infrastructure errors outside this contract.

| HTTP status | Stable problem type | Meaning |
| --- | --- | --- |
| 400 | `urn:task-manager:problem:validation` | Invalid request or incorrect current password |
| 401 | `urn:task-manager:problem:unauthorized` | Invalid login or missing/invalid/expired token |
| 403 | `urn:task-manager:problem:forbidden` | ADMIN role missing |
| 404 | `urn:task-manager:problem:not-found` | Missing resource/recipient or inaccessible task |
| 405 | `urn:task-manager:problem:method-not-allowed` | Unsupported method; include Allow header |
| 409 | `urn:task-manager:problem:email-conflict` | Email already registered |
| 409 | `urn:task-manager:problem:assignee-disabled` | Selected recipient is disabled |
| 415 | `urn:task-manager:problem:unsupported-media-type` | Unsupported request media type |
| 500 | `urn:task-manager:problem:internal-error` | Unexpected failure with safe generic detail |

On personal routes, missing and other-assignee tasks produce identical 404 details,
even for ADMIN. On admin routes, non-admin callers receive 403 before task lookup;
authorized admins receive 404 only for missing tasks. Protected-resource 401 responses
include WWW-Authenticate: Bearer;
invalid or expired tokens add error="invalid_token". Login 401 responses use the
generic detail `Invalid email or password.` and do not require a bearer challenge.
Unmatched protected routes still require authentication; authenticated unknown
routes return a Problem Details 404. Never disclose stack traces, SQL, secrets,
or internal exception messages.

## Implementation verification

The task domain has 11 passing tests covering creation defaults, Unicode length
limits, required fields, all status transitions, optional-field clearing, immutable
identity/attribution, restoration, and rejected updates leaving state unchanged.
Layer and Modulith checks also pass. This verifies domain behavior only; the task
HTTP behavior is verified separately by `TaskHttpTests` using signed JWTs and H2.

Application use cases now have 17 passing in-memory tests for assignment, personal/admin access,
partial updates, list scoping/filter forwarding, and audit requests/failure propagation.
Persistence adapters and transactional wiring now have eight passing H2 integration
tests, including database filtering, audit survival, and rollback after audit flush.
All nine task PostgreSQL migration/integration tests also pass with Docker access.
The 44 HTTP tests cover personal/admin access, creation URLs, request validation, partial
updates, list filters, and audit persistence. See the
[backend implementation sequence](../../backend/README.md#task-domain-and-next-steps).

The latest `.\mvnw.cmd clean verify` run passed all 207 tests and packaged the
executable JAR. This includes 182 Docker-free tests and 25 PostgreSQL-backed tests;
the nine task PostgreSQL tests verify V2 migration/constraints and audit rollback.
The previous Docker/cache access blockers are resolved by running with the required
process access. See the [verification notes](../../backend/README.md#run-and-verify).

Before implementation is considered complete, verify schema conformance, pagination
and filtering, every status transition, nullable patch fields, unknown-field rejection,
Unicode password byte limits, duplicate email handling, and empty response bodies
for 204. Verify self-assignment and attribution spoofing rejection for USER and ADMIN.
Verify user isolation, assignee-scoped personal routes for both roles, global admin
access to user-created and other-admin-created tasks, and 403 before admin task lookup.
Verify admin demotion after token expiry, filtered list totals without duplicates,
Location URLs accessible to creators, and transactional admin mutation audit records.
Verify assignment to USER and ADMIN recipients, missing/disabled recipients,
non-admin assignment rejection before recipient lookup, immutable attribution fields,
registration role escalation rejection, ADMIN checks, generic login failures,
disabled-account login rejection, and the accepted stale-token behavior. Runtime
OpenAPI comparison now runs in the Maven test suite to detect contract drift.
