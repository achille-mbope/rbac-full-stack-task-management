# ADR-0003: Stateless JWT Authentication

**Status:** Accepted  
**Date:** 2026-09-15  
**Updated:** 2026-09-23

## Context

The Angular SPA needs authenticated REST requests. Version one uses bearer
tokens without server-side sessions or refresh tokens.

## Decision

- Issue HS256-signed JWT access tokens with a **15-minute lifetime**.
- Accept tokens only through `Authorization: Bearer <token>`, never query
  parameters or authentication cookies.
- Store the token in frontend memory, not `localStorage` or `sessionStorage`.
  Page reload and expiration require login again.
- Include stable user ID (`sub`), roles, `iss`, `aud`, `iat`, and `exp`.
  Validate signature, allowed algorithm, issuer, audience, and expiration.
- Load `JWT_SECRET` from the environment: at least 256 bits of cryptographically
  random key material. Fail startup for missing or invalid configuration.
- Disabled accounts cannot obtain new tokens.

### Token lifecycle

- **Logout:** clear frontend memory. A copied token remains valid until expiration.
- **Password, role, or account-status changes:** existing tokens retain their
  previous authority until expiration, at most 15 minutes after issuance.
- **Key rotation:** replacing the single active key consistently across backend
  instances invalidates all outstanding tokens and requires login.
- **Immediate per-user revocation:** outside v1 scope. A denylist or token-version
  lookup would require revisiting the statelessness decision.

## Rationale

Bearer tokens support the SPA and API clients without session storage. Short
expiry bounds stale authorization. Memory storage avoids persistent browser
credentials, but does not eliminate XSS risk.

## Alternatives

- **HttpOnly session cookies:** viable, with session management and CSRF protection.
- **Persistent browser token storage:** convenient across reloads, but exposes
  persistent tokens to JavaScript and theft through XSS.
- **OIDC identity provider:** useful for managed identity and federation, outside v1 scope.

## Consequences

- Frequent login and delayed revocation are explicit v1 tradeoffs.
- Stateless authentication does not make application or database scaling automatic.
- CSRF may be disabled only for this bearer-header-only model with no ambient
  browser credentials; see [ADR-0006](0006-security.md).
- The README must document expiry, reload behavior, and revocation limitations.

## References

- [OWASP JWT guidance](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_Cheat_Sheet.html)
- [OWASP browser storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html)
