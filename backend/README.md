# Backend

The backend uses Java 21, Spring Boot 3.5.16, Spring Modulith 1.4.13, and the committed Maven Wrapper.

## Current scope

The `user` module implements account services, persistence, and account HTTP adapters.
The `auth` module implements login, JWT issuance/validation, and stateless HTTP security.
The `common` module provides consistent Problem Details responses and a shared clock.
Task endpoints, the Angular UI, and runtime OpenAPI/Swagger UI remain pending.

## HTTP endpoints

| Method | Path | Access |
| --- | --- | --- |
| POST | `/api/v1/auth/register` | Public; creates an enabled USER, without issuing a token |
| POST | `/api/v1/auth/login` | Public; returns a 900-second bearer token with `Cache-Control: no-store` |
| PUT | `/api/v1/users/me/password` | Authenticated; returns 204 |
| GET | `/api/v1/admin/users` | ADMIN; paginated account list |
| PUT | `/api/v1/admin/users/{userId}/roles` | ADMIN; replaces roles |
| PUT | `/api/v1/admin/users/{userId}/status` | ADMIN; enables/disables an account |

JSON request/response shapes follow [the API contract](../docs/api/openapi.json).
Unknown JSON fields are rejected, including roles supplied during registration.
All controlled errors, including authentication and CORS failures, use
`application/problem+json`. Invalid login responses do not reveal account status.

## Test registration and login with Postman

Start the backend from the repository root in PowerShell (Java 21+ required):

```powershell
cd backend
.\setup-local.ps1
.\mvnw.cmd spring-boot:run
```

Run setup once per checkout, then leave the backend running while sending requests.
The default H2 profile needs no Docker or separate database. These requests use
`http://localhost:8080`; the frontend and Swagger UI are not implemented yet.

### Register an account

In Postman, create a request with:

- Method: **POST**
- URL: `http://localhost:8080/api/v1/auth/register`
- **Authorization**: **No Auth**
- **Body**: **raw**, with **JSON** selected (`Content-Type: application/json`)

```json
{
  "email": "alice@example.com",
  "password": "a long original password"
}
```

Click **Send**. Expect **201 Created** with account details and the `USER` role.
Registration does not issue a token. Send only `email` and `password`; extra fields
such as `roles` are rejected. New passwords require at least 15 Unicode code points
and at most 72 UTF-8 bytes. Reusing an existing email returns **409 Conflict**.

### Log in

Create a second **POST** request to
`http://localhost:8080/api/v1/auth/login`. Select **No Auth** and **Body > raw > JSON**,
then send the same email and password. Expect **200 OK** with this response shape:

```json
{
  "accessToken": "<your JWT>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

Copy the `accessToken` value. It expires after 15 minutes; log in again for a new
token. There is no refresh-token endpoint.

### Send the token and check permissions

Create a **GET** request to `http://localhost:8080/api/v1/admin/users`.
Under **Authorization**, select **Bearer Token** and paste the token into **Token**
without adding the word `Bearer`. The resulting header is
`Authorization: Bearer <your JWT>`.

A newly registered USER receives **403 Forbidden** on this ADMIN-only endpoint.
This is the expected permission check. Switching to **No Auth** returns
**401 Unauthorized**. An administrator can receive **200 OK**; see
[administrator provisioning](#administrator-provisioning-and-recovery).

| Check | Expected response |
| --- | --- |
| Register a new email with a valid password | 201 Created |
| Register the same email again | 409 Conflict |
| Register with password `short` or an extra `roles` field | 400 Bad Request |
| Log in with valid credentials | 200 OK with a bearer token |
| Log in with an incorrect password | 401 Unauthorized |
| GET `/api/v1/admin/users` without a token or with an expired token | 401 Unauthorized |
| GET `/api/v1/admin/users` with a valid USER token | 403 Forbidden |

Errors use `application/problem+json`. If Postman cannot connect, confirm the
backend has finished starting and is still running on port 8080. Restarting the
default H2 backend deletes accounts: register again before logging in.

To run the automated account HTTP/JWT tests from `backend/`:

```powershell
.\mvnw.cmd "-Dtest=UserHttpTests" test
```

These tests supply their own signing key and application context; they need neither
Docker nor a running backend.

## Authentication configuration

For local H2 development, run this one-time setup from `backend/`:

```powershell
.\setup-local.ps1
```

It generates 32 random bytes and saves the Base64-encoded signing key in
`backend/.local/application.properties`, which Git ignores. The script preserves
an existing file.

After setup, start the application with:

```powershell
.\mvnw.cmd spring-boot:run
```

The H2 profile imports that optional file relative to the application's working
directory. An environment `JWT_SECRET` takes precedence. Keep the same local file
to reuse the signing key across restarts; H2 account data is still disposable.
A fresh checkout needs the one-time setup again because the file is not committed.

For PostgreSQL/deployed environments, supply `JWT_SECRET` externally as Base64-encoded,
cryptographically random key material of at least 32 bytes. The local file is imported
only by the H2 profile. Missing, malformed, or short keys still fail startup.
Test resources supply a separate test-only fixture.

Keep production keys in environment/secret management. Reuse the configured key across
instances; replacing it invalidates outstanding tokens. Never log or commit the key.
Tokens use HS256, issuer `task-manager`, audience `task-manager-api`, a UUID subject,
and USER/ADMIN roles. Validation checks the signature, algorithm, issuer, audience,
required claims, and expiration with no expiry grace period.

Only `Authorization: Bearer <token>` authenticates requests. Sessions, form login,
HTTP Basic, cookie authentication, and query-string tokens are not used. Existing tokens
retain their authority after password, role, or enabled-status changes until expiry;
disabled accounts cannot log in again. Logout is client-side token removal.

Set `CORS_ALLOWED_ORIGINS` to a comma-separated list of explicit browser origins.
H2 development defaults to `http://localhost:4200`; other profiles default to no
cross-origin access. Wildcards are rejected. Deployed environments require HTTPS.
Login/registration rate limiting remains deferred and is required before public deployment.

## Security and HTTP code ownership

| Component | Responsibility |
| --- | --- |
| `auth.internal.presentation.LoginController` | Accept login requests and return typed, non-cacheable token responses |
| `auth.internal.application.LoginService` | Verify credentials through the public user API and request token issuance |
| `auth.internal.presentation.LoginProblemAdvice` | Map rejected credentials to a generic 401 |
| `auth.internal.infrastructure.HttpSecurityConfiguration` | Wire the stateless filter chain and route authorization |
| `auth.internal.infrastructure.CorsPolicy` | Configure allowed origins and preflight handling |
| `auth.internal.infrastructure.SecurityProblemHandlers` | Write authentication/authorization Problem Details from security filters |
| `auth.internal.infrastructure.JwtConfiguration` | Load the signing key and configure the encoder/decoder |
| `auth.internal.infrastructure.JwtPolicy`, `JwtClaimsValidator`, `JwtTokenIssuer` | Define token settings, validate claims, and issue tokens |
| `user.internal.presentation` | Separate registration, password, and account-administration controllers and their request DTOs |
| `common.internal.presentation` | Strict JSON configuration and safe, shared HTTP validation/error mapping |

`HttpSecurityConfiguration` activates only for servlet web applications. Its
stateless filter chain disables request caching, so unauthenticated requests are
not saved for replay after login. The JWT authentication converter reads the
`roles` claim and adds the `ROLE_` prefix, mapping ADMIN to `ROLE_ADMIN` for
the route's `hasRole("ADMIN")` check.

The filter chain allows public registration/login paths, requires ADMIN for
`/api/v1/admin/**`, and requires authentication for other routes. Controllers still
determine allowed HTTP methods. Unknown protected paths return 401 without a token
and 404 for an authenticated caller who passes the route's role requirement.
ADMIN authorization runs before request-body handling, then application services
check it again before business validation or target lookup.

The chain disables CSRF because authentication uses bearer headers without ambient
browser credentials. Adding authentication cookies requires revisiting that decision.
CORS runs before bearer authentication so allowed preflight requests need no token.
Its filter is installed only in the security chain, not as a second servlet filter.
Internal error dispatches are allowed so error responses can be rendered; this
does not expose normal protected requests.

## User module API

Public contracts and immutable DTOs live in `net.mbope.taskmanager.user`.
Implementation code lives under `user.internal`:

- `domain`: framework-independent `UserAccount`, immutable `AccountRoles`, and password policy.
- `application`: registration, credentials, lookup, and administration orchestration.
- `application.port`: contracts for account storage, current principal, email normalization, and password hashing.
- `infrastructure`: JPA mapping and storage, Spring Security, BCrypt, Jakarta email validation, and configuration.
- `presentation`: account controllers, request DTOs, and account error mapping.

Public internal types enable collaboration between these packages; Spring Modulith
still prevents other modules from accessing them. Application services own transactions
and depend on ports rather than infrastructure. Domain code has no Spring, JPA, or
HTTP dependencies. Controllers call public user contracts, never persistence ports.
Tests mirror the owning layer; shared module contract tests run against H2 and PostgreSQL.
Project-wide `LayerArchitectureTests` enforce dependency direction for domain,
application, and presentation packages. Controllers handle one use case group each;
security configuration delegates CORS, JWT claim validation, and security errors
to dedicated components.

| Contract | Responsibility |
| --- | --- |
| `AccountRegistration` | Register an enabled USER using email and password only |
| `UserCredentials` | Verify credentials; change the authenticated account's password |
| `AccountLookup` | Return stable ID and enabled status for assignment validation |
| `AccountAdministration` | ADMIN-only paginated listing, role replacement, enabling/disabling |

Email addresses are stripped of surrounding whitespace and lowercased with
`Locale.ROOT`. A named database constraint enforces uniqueness, including concurrent
registration. `RegistrationService` validates input and hashes passwords through ports,
then creates a domain account. `JpaAccountStore` maps domain accounts to JPA entities,
flushes new registrations, and translates only the email uniqueness constraint into
`EmailAlreadyRegisteredException`; unrelated database failures remain failures.
The exception remains a framework-independent business failure in the public API.
All account use cases access persistence through `AccountStore`. Domain role values
enforce the mandatory USER grant before changes reach persistence.

New passwords require 15 Unicode code points and at most 72 UTF-8 bytes.
Passwords are preserved exactly and hashed with BCrypt strength 12. Credential
verification returns an empty optional for unknown, disabled, and incorrect-password
accounts, and performs a hash comparison in all three cases. Account views and
identity results never expose password hashes.

Every account has USER. The database stores the additional ADMIN grant as
`is_admin`; this representation is intentionally limited to the two v1 roles.
Role replacement must retain USER. Account IDs and creation times are immutable.
Updates follow the contract's last-write-wins policy; Hibernate updates only changed
columns so a password update does not overwrite an unrelated role/status change.

Application services check the authenticated principal through the `CurrentAccount` port;
its Spring Security adapter reads the security context.
The auth module sets the principal name to the account UUID and maps
token roles to `ROLE_USER`/`ROLE_ADMIN`. Admin checks precede validation and target
lookup. Password changes have no caller-supplied account ID. Authorization uses the
current principal's authorities, preserving the accepted JWT expiry semantics after
role/status changes.

The user module depends only on `common` for HTTP errors and shared infrastructure.
The auth module consumes public user contracts and common HTTP error support.
Constructor injection, focused interfaces, and separate registration, credential, lookup, and administration services keep these
responsibilities independent.

## Run and verify

From `backend/`, with Java 21+ and Docker running:

```powershell
.\mvnw.cmd verify
```

On Unix, use `./mvnw verify`. Verification includes PostgreSQL 16 Testcontainers, HTTP/JWT tests,
Flyway migrations with Hibernate schema validation, shared account contract tests on H2,
password/email unit tests, authorization tests, and Spring Modulith verification.
Docker-dependent tests fail if Docker is unavailable; they are not silently skipped.
Module documentation is generated under `target/spring-modulith-docs/`.

For a clean package build with all tests that do not require Docker:

```powershell
.\mvnw.cmd "-Dtest=*,!UserModuleTests,!TaskManagerApplicationTests" clean verify
```

The latest clean run passed 92 tests. The two excluded classes require PostgreSQL
Testcontainers. Run the full `verify` command with Docker available before treating
PostgreSQL behavior as verified for the current revision. Tests supply their own
signing-key fixture; application startup requires either the H2 local setup or an external `JWT_SECRET`.

The tests were introduced before their implementations. The development-profile
duplicate-email test also reproduced an H2/PostgreSQL constraint-name difference
before the exception translation was corrected.

After the one-time local setup above, start the disposable H2 application:

```powershell
.\mvnw.cmd spring-boot:run
```

The default `h2` profile uses an in-memory database and Hibernate create/drop, with
Flyway disabled. Data is lost on shutdown. For a persistent PostgreSQL 16 instance,
set `SPRING_PROFILES_ACTIVE=postgres`, `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`
in the environment, then use the same startup command. This profile runs Flyway
and validates the schema; it never creates/drops it through Hibernate.
Docker Compose is not supplied yet.

## Administrator provisioning and recovery

No default administrator or credentials are shipped. Register the intended operator
with `POST /api/v1/auth/register` using email and password, then use an authorized
PostgreSQL operator connection to promote that existing account. The same operation
recovers a demoted or disabled administrator:

```sql
-- In psql, prompt for the existing account's email (not its password).
\prompt 'Administrator account email: ' admin_email
BEGIN;
UPDATE user_accounts
SET is_admin = TRUE, enabled = TRUE, updated_at = CURRENT_TIMESTAMP
WHERE email = lower(btrim(:'admin_email'))
RETURNING id, email;
COMMIT;
```

Confirm that exactly the intended account was returned. No returned row means no
account was changed. Sign in with `POST /api/v1/auth/login` to obtain a token with
the new authorities. This procedure does not reset or expose credentials. HTTP tests
cover registration, operator promotion, administrator login, and account management.
