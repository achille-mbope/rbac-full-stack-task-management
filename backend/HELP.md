# Backend references

Use [the backend guide](README.md) for current setup, API availability, module contracts,
and verification commands, and [the project README](../README.md) for delivery status.

The project uses Java 21, Spring Boot 3.5.16, and Spring Modulith 1.4.13; exact
dependencies are defined in [pom.xml](pom.xml). Maven is pinned by the
[wrapper configuration](.mvn/wrapper/maven-wrapper.properties).

The default development profile uses disposable H2. PostgreSQL tests use
`postgres:16-alpine` and require a Docker-compatible runtime. See
[TestcontainersConfiguration](src/test/java/net/mbope/taskmanager/TestcontainersConfiguration.java).
Docker Compose remains planned.

Task domain and application tests run without a database. Persistence and audit
adapters are wired and covered by H2 integration tests. PostgreSQL verification
requires Docker; task HTTP endpoints remain pending.
See [task implementation notes](README.md#task-domain-and-next-steps) and
[verification results](README.md#run-and-verify).
