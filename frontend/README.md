# Task Manager frontend

Angular 21 client-side application with standalone components, lazy routes, and SCSS.
SSR is not enabled. The scaffold includes a responsive navigation shell and preview
pages; authentication, route guards, account loading, and task operations are pending.

## Run locally

Use Node.js compatible with the installed Angular 21 release (22.12+ on the Node 22
line, or 24+) and npm. From `frontend/`:

```powershell
npm ci
npm start
```

Open http://localhost:4200. Start the backend separately using the
[backend setup guide](../backend/README.md). Local H2 normally listens on port 8080.

## Routing and API proxy

| URL            | Page                        |
| -------------- | --------------------------- |
| `/`            | Redirects to `/home`        |
| `/home`        | Overview                    |
| `/tasks`       | Task workspace preview      |
| `/users`       | User administration preview |
| Unknown routes | Redirect to `/home`         |

Navigation uses Angular Router, lazy page imports, page titles, and active-link state.
The preview routes are public and load no protected data. Add authentication and
ADMIN guards with the corresponding features; the backend remains the authority
for API access.

`HttpClient` is registered in `app.config.ts`. Future services should request
relative URLs such as `/api/v1/tasks`. During `npm start`, `proxy.conf.json` forwards
all `/api/**` requests to `http://127.0.0.1:8080` without changing the path.
Restart the development server after editing the proxy. See
[Angular proxy documentation](https://angular.dev/tools/cli/serve#proxying-to-a-backend-server).

A 401 from an unauthenticated protected endpoint is expected; the proxy does not
bypass authentication. Connection errors indicate the backend is unavailable or
the target port is wrong.

## Build and verify

Run from `frontend/`:

```powershell
npm run build
npm test -- --watch=false
```

Recorded verification on 2026-09-29:

- Production build passed, including separate lazy chunks for all three pages.
- Six tests passed, covering component creation, root/unknown-route redirects,
  lazy-page loading, and navigation with active-link state.
- With the H2 backend running, `/api/v1/tasks` through port 4200 returned the
  expected 401 Problem Details response and backend-generated request ID.
- Direct loading of `/tasks` returned the app shell. Visual browser inspection
  was unavailable; responsive appearance and keyboard behavior still need manual review.

To repeat the proxy check with both servers running:

```powershell
curl.exe -i http://localhost:4200/api/v1/tasks
```

Expect HTTP 401 with `application/problem+json`, not an HTML page. On Unix, use `curl`.

## Production hosting

Production files are emitted to `dist/Taskmanager/browser/`. Serve them with an
SPA fallback to `index.html` for frontend deep links. Configure production
`/api/**` forwarding to Spring Boot before the SPA fallback; Angular's development
proxy is not included in the production build. Follow the
[deployment guide](../docs/deployment.md) for HTTPS and backend proxy trust.

## Next implementation milestone

Add registration/login, in-memory JWT storage, a bearer interceptor, route guards,
expiry handling, and client-side logout. Then connect task listing, creation, editing,
completion, and deletion. Implement ADMIN-only user management after authentication.
The first end-to-end milestone is registration, login, task creation/completion,
and logout through the browser.
