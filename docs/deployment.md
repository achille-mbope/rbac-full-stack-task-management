# Production deployment

The backend provides a guarded production profile, authentication request limits,
and operational diagnostics. Operators supply TLS infrastructure, secret delivery,
backups, and alerting.

## Startup configuration

Set `SPRING_PROFILES_ACTIVE=prod`. This profile group includes `postgres` and
requires `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET`.
Use persistent PostgreSQL, a dedicated database account, and a unique Base64-encoded
random signing key of at least 32 bytes. Supply secrets through the deployment
platform; never copy the local development key or test fixture.
Bare application startup still defaults to disposable H2 for local development:
always select `prod` explicitly in the deployment definition.

The `postgres` profile alone configures persistence without activating the production
guard or mandatory HTTPS. Do not use it as a substitute for `prod` on deployed hosts.

The production guard runs before datasource, Flyway, or Hibernate beans initialize.
It rejects H2, non-PostgreSQL URLs, empty database credentials, schema creation/update,
disabled Flyway, enabled Swagger/H2 console/SQL printing, disabled HTTPS enforcement
or rate limiting, unsafe management exposure, and detailed default error responses.
Hibernate validates the schema; Flyway applies migrations. Schedule migrations and
back up the database before releases; test restoration and release recovery separately.

Documentation is disabled in `prod`; `API_DOCS_ENABLED` cannot enable it there.
Overriding the underlying documentation properties to true causes startup to fail.
Production CORS origins must be explicit HTTPS origins; leaving `CORS_ALLOWED_ORIGINS`
empty denies cross-origin browser requests.

## HTTPS and trusted proxies

Choose one transport configuration in your external application properties file.
For TLS at the application, configure a certificate/key store with Spring Boot's
`server.ssl.*` properties, set `server.ssl.enabled=true`, and retain
`server.forward-headers-strategy=none`.

For TLS terminated at a reverse proxy, use a narrow proxy IP pattern, for example:

```properties
server.forward-headers-strategy=native
server.tomcat.remoteip.internal-proxies=10[.]0[.]0[.]10
```

Replace the example with actual proxy addresses. Do not trust all addresses.
Keep the application port private and reachable only by the proxy/health checker.
The proxy must strip caller-supplied forwarding headers and set the real client IP
and HTTPS scheme. Native Tomcat processing trusts configured proxy peers; the
application limiter never parses `X-Forwarded-For` itself. Framework forwarding
and blank or unrestricted `.*`/`.+` proxy patterns are rejected by the guard.
Operators remain responsible for reviewing other patterns for overly broad trust.

Reject HTTP credentials at the public edge. The application also requires secure
requests and rejects insecure traffic with 403 `https-required` Problem Details.
This cannot protect credentials already sent over HTTP. Configure health checks through HTTPS or the trusted proxy.

Build and start the packaged application with externally supplied settings:

```powershell
cd backend
.\mvnw.cmd clean verify
$env:SPRING_PROFILES_ACTIVE = "prod"
# Supply database and JWT secrets through your deployment platform.
java -jar target/TaskManager-0.0.1-SNAPSHOT.jar --spring.config.additional-location=file:/etc/taskmanager/application.properties
```

Use an external configuration path appropriate to the deployment host. Containers
are optional; this change does not provide a Dockerfile or production orchestration.

## Authentication request limits

POST login and registration requests are limited before JSON parsing and password
hashing. Every admitted attempt counts, including successful and malformed requests.
OPTIONS preflight is excluded; other API operations do not consume these quotas.

| Property | Default |
| --- | --- |
| `app.rate-limit.enabled` | `true`; cannot be disabled in prod |
| `app.rate-limit.window-seconds` | 60 |
| `app.rate-limit.login-per-client` | 20 |
| `app.rate-limit.register-per-client` | 5 |
| `app.rate-limit.login-global` | 200 |
| `app.rate-limit.register-global` | 50 |
| `app.rate-limit.max-clients` | 10000 |

Configure these properties in the external configuration file. Limits must be
positive; the window is capped at 3600 seconds and client tracking at 100000 entries.
Quotas are separate per operation and keyed by the container-resolved client IP.
Process-wide limits constrain clients rotating addresses. At tracking capacity,
new addresses are rejected until the next window rather than evicting active quotas.

Rejection returns HTTP 429 with `application/problem+json`, type
`urn:task-manager:problem:rate-limited`, `Retry-After` in whole seconds, and
`Cache-Control: no-store`. CORS exposes `Retry-After` and `X-Request-ID` to allowed origins.

This is bounded, in-memory, fixed-window protection for one application process.
Counters reset on restart, boundary bursts are possible, and clients behind a shared
NAT share a quota. Tune limits after benchmarking BCrypt on deployment hardware.
Multiple instances require a shared gateway/distributed limiter; add account-aware
abuse controls there for credential stuffing. These quotas do not replace edge
connection limits, JSON body-size limits, timeouts, or bot protection.

## Health, logs, and metrics

Only GET of these management URLs is public, over the configured secure transport:

| URL | Meaning |
| --- | --- |
| `/actuator/health` | Overall health, status only |
| `/actuator/health/liveness` | Process liveness; independent of PostgreSQL |
| `/actuator/health/readiness` | Readiness state and database connectivity |

Health details and component names are hidden. Other management routes are denied,
even to ADMIN, and management JMX exposure is disabled. PostgreSQL outages affect
readiness rather than liveness, avoiding database-driven restart loops.
Shutdown is graceful with a 30-second shutdown-phase timeout; give the process
sufficient termination time in the deployment platform.

Application-filter responses carry a server-generated UUID in `X-Request-ID`.
Caller-provided IDs are ignored. The ID is included in logging MDC and removed or
restored after the request. Unexpected MVC failures log the ID, exception class,
and stack locations; this handler omits messages, causes, request bodies, query
strings, and credentials. Clients still receive a generic 500 Problem Detail.
Restrict log access and retention, and avoid request-body/SQL parameter logging.
Errors rejected before the application filter chain may not carry a request ID.

Actuator instruments HTTP requests and JVM/process metrics. The limiter records
`auth.rate.limit.rejections` with fixed `operation=login|register` tags; IPs and
account identifiers are not labels. Metrics remain internal by default. Connect
an approved registry exporter before relying on metric-based alerts; no public
metrics, environment, configuration, or heap-dump endpoint is enabled.
Monitor readiness, HTTP 5xx responses, rejection rates, and resource saturation.

## Verification and release checks

From `backend/`, focused checks without Docker:

```powershell
.\mvnw.cmd "-Dtest=ProductionConfigurationTests,AuthenticationRateLimiterTests,OperationalHttpTests,DiagnosticsTests,OpenApiTests" test
```

With Docker, run `.\mvnw.cmd clean verify`. `ProductionHttpTests` starts the prod
profile with PostgreSQL migrations and an embedded server; it checks secure health
and API access, and rejection of spoofed HTTPS headers from an untrusted network peer.
Configuration tests reject unsafe overrides before other beans initialize. Limiter
tests cover expiry, separate/global quotas, capacity, concurrency, CORS, and spoofing.
Diagnostics tests check correlation cleanup and exclusion of secret exception data.

Latest verification (2026-09-28): `clean verify` passed all 246 tests, including
218 Docker-free and 28 PostgreSQL-backed tests, and packaged the executable JAR.

Before public release, verify the actual certificate/proxy setup, perform a backup
restoration drill, scan release dependencies, and run registration/login/task smoke
tests through the public HTTPS URL. Existing 15-minute JWT revocation limitations
and operator administrator recovery procedures still apply.

## References

- [Spring Boot forwarding configuration](https://docs.spring.io/spring-boot/3.5/how-to/webserver.html)
- [Spring Boot management security and probes](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html)
- [OWASP logging guidance](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)
