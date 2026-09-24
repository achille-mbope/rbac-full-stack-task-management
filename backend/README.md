# Backend

The backend uses Java 21, Spring Boot 3.5.16, Spring Modulith 1.4.13, and the committed Maven Wrapper.

## Current scope

The `user` module implements the account application services and persistence.
Registration/login HTTP adapters, JWT issuance/validation, account REST controllers,
and the Angular UI are the next integration steps. There are no application REST
endpoints yet. Spring Security's default protection remains in place.

## User module API

Public contracts and immutable DTOs live in `net.mbope.taskmanager.user`.
Implementation code lives under `user.internal`:

- `domain`: framework-independent `UserAccount`, immutable `AccountRoles`, and password policy.
- `application`: registration, credentials, lookup, and administration orchestration.
- `application.port`: contracts for account storage, current principal, email normalization, and password hashing.
- `infrastructure`: JPA mapping and storage, Spring Security, BCrypt, Jakarta email validation, and configuration.

Public internal types enable collaboration between these packages; Spring Modulith
still prevents other modules from accessing them. Application services own transactions
and depend on ports rather than infrastructure. Domain code has no Spring, JPA, or
HTTP dependencies. A presentation package will be introduced with HTTP controllers.
Tests mirror the owning layer; shared module contract tests run against H2 and PostgreSQL.
ArchUnit tests enforce dependency direction.

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
The future auth module must set the principal name to the account UUID and map
token roles to `ROLE_USER`/`ROLE_ADMIN`. Admin checks precede validation and target
lookup. Password changes have no caller-supplied account ID. Authorization uses the
current principal's authorities, preserving the accepted JWT expiry semantics after
role/status changes.

The module currently has an empty dependency allowlist. Add `common` only when an
actual shared type is introduced. Constructor injection, focused interfaces, and
separate registration, credential, lookup, and administration services keep these
responsibilities independent.

## Run and verify

From `backend/`, with Java 21+ and Docker running:

```powershell
.\mvnw.cmd verify
```

On Unix, use `./mvnw verify`. Verification includes PostgreSQL 16 Testcontainers,
Flyway migrations with Hibernate schema validation, an H2 development smoke test,
password/email unit tests, authorization tests, and Spring Modulith verification.
Docker-dependent tests fail if Docker is unavailable; they are not silently skipped.
Module documentation is generated under `target/spring-modulith-docs/`.

For the complete suite that does not require Docker:

```powershell
.\mvnw.cmd "-Dtest=*,!UserModuleTests,!TaskManagerApplicationTests" test
```

The tests were introduced before their implementations. The development-profile
duplicate-email test also reproduced an H2/PostgreSQL constraint-name difference
before the exception translation was corrected.

Start the disposable H2 application:

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

No default administrator or credentials are shipped. Once the auth module exposes
registration, register the intended operator account normally, then use an authorized
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
account was changed. Sign in again to obtain a token with the new authorities once
auth is implemented. This procedure does not reset or expose credentials. Initial
account creation through HTTP and an end-to-end bootstrap check remain part of
the upcoming auth integration.
