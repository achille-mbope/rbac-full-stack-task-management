# ADR-0005: Reproducible Maven and Angular Builds

**Status:** Accepted  
**Date:** 2026-09-15  
**Updated:** 2026-09-29

## Context

Both applications need reproducible toolchains. These are project baselines,
not claims about the newest or most popular releases.

## Decision

- **Backend:** Java 21 LTS, Spring Boot 3.5.x, Maven.
- Commit Maven Wrapper scripts/configuration in `backend/`, pinning the Maven version.
- Use the Spring Modulith 1.4.x BOM compatible with Spring Boot 3.5.
- **Frontend:** Angular 21 with matching CLI and, if used, Material major versions.
- Use Node.js 22, at least 22.12.0, pinning an exact patch in `frontend/.nvmrc`
  when scaffolding.
- Use npm, record its exact version in `package.json`, commit
  `package-lock.json`, and install with `npm ci`.
- Pin exact framework/plugin versions in build files at scaffolding time.
  Select TypeScript and RxJS versions from Angular's compatibility table.

## Rationale

The existing frontend was scaffolded with Angular 21. The application layout and
routing work retain that major version; the earlier Angular 20 baseline is superseded.
The current scaffold uses dependency ranges with exact resolutions in the npm lockfile.
Exact manifest versions and a Node patch pin remain toolchain follow-up work.

Maven's standard lifecycle fits this backend. Java 21 supplies the required
language features. Retaining the chosen framework lines avoids an unnecessary
major-version migration during initial implementation.

## Alternatives

- **Gradle:** viable; additional build customization is not needed here.
- **Newer Java/framework majors:** reconsider for required features, compatibility,
  security updates, or maintenance support.

## Consequences

- Set `maven.compiler.release=21`; do not depend on newer Java features.
- From `backend/`, run `./mvnw verify` on Unix or `.\mvnw.cmd verify` on
  Windows, including module verification and PostgreSQL integration tests.
- From `frontend/`, run `npm ci`, `npm run build`, and `npm test -- --watch=false`.
- Review upstream support and security updates before deployment, upgrading
  baselines if necessary. These choices do not guarantee continuing support.
- Exact versions belong in build files and lockfiles, not duplicated here.

## References

- [Spring Modulith compatibility](https://docs.spring.io/spring-modulith/reference/appendix.html)
- [Angular compatibility](https://angular.dev/reference/versions)
