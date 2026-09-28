# Local PostgreSQL with Docker Compose

The root `docker-compose.yml` starts PostgreSQL 16 with a persistent named volume,
a readiness check, and a port bound only to `127.0.0.1`. It runs the database;
start the backend separately with the Maven Wrapper. Docker Compose v2 and a
running Docker-compatible engine are required.

From the repository root in PowerShell:

```powershell
$env:POSTGRES_PASSWORD = 'replace-with-a-local-development-password'
docker compose up -d --wait

$env:SPRING_PROFILES_ACTIVE = 'postgres'
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:5432/taskmanager'
$env:DB_USERNAME = 'taskmanager'
$env:DB_PASSWORD = $env:POSTGRES_PASSWORD
$key = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($key) } finally { $rng.Dispose() }
$env:JWT_SECRET = [Convert]::ToBase64String($key)

cd backend
.\mvnw.cmd spring-boot:run
```

The backend starts at `http://localhost:8080`. Flyway applies migrations and Hibernate
validates the schema. Swagger is disabled by default in `postgres`; for local API
exploration, set `API_DOCS_ENABLED=true` before startup. The `postgres` profile does
not enable production safeguards; deployed hosts must use `prod` and the
[deployment guide](deployment.md).

Alternatively, copy `.env.example` to `.env` and supply a password there for Compose.
Compose reads `.env`; Maven and Java do not. Supply the matching `DB_*` variables
and `JWT_SECRET` to the backend process separately. Do not commit `.env`.

| Compose setting | Default | Matching backend setting |
| --- | --- | --- |
| `POSTGRES_DB` | `taskmanager` | Database path in `DB_URL` |
| `POSTGRES_USER` | `taskmanager` | `DB_USERNAME` |
| `POSTGRES_PASSWORD` | Required, no default | `DB_PASSWORD` |
| `POSTGRES_PORT` | `5432` on loopback | Port in `DB_URL` |

If port 5432 is occupied, set `POSTGRES_PORT` to an unused port and update `DB_URL`.
These database credentials are for local development. Changing initialization
variables does not change credentials in an existing PostgreSQL volume.

From the repository root, `docker compose ps` shows health and
`docker compose logs postgres` shows database logs. `docker compose down` stops and
removes the container/network while retaining data. `docker compose up -d --wait`
reuses that data. Only when intentionally discarding the local database, run
`docker compose down --volumes`; this deletes all accounts and tasks in its volume.

The Maven Testcontainers suites and [packaged production smoke test](deployment.md#packaged-production-smoke-test)
use isolated databases and do not use this development volume.
