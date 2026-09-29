# Task Manager frontend

Angular 21 client-side application with standalone components, lazy routes, and SCSS.
SSR is not enabled. The frontend includes registration/login, guarded routes,
in-memory authentication, and personal task management. Account administration
and administrative task workflows remain pending.

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

| URL            | Page                      |
| -------------- | ------------------------- |
| `/`            | Redirects to `/home`      |
| `/login`       | Sign in (guests)          |
| `/register`    | Create account (guests)   |
| `/home`        | Overview (signed in)      |
| `/tasks`       | My tasks (signed in)      |
| `/users`       | User preview (ADMIN only) |
| Unknown routes | Redirect to `/home`       |

Navigation uses Angular Router, lazy page imports, page titles, and active-link state.
Workspace routes require login; `/users` also requires ADMIN. The backend remains
the authority for API access. My tasks loads the caller's assigned tasks; Users
remains a preview without account data.

`HttpClient` is registered in `app.config.ts`. Services request
relative URLs such as `/api/v1/tasks`. During `npm start`, `proxy.conf.json` forwards
all `/api/**` requests to `http://127.0.0.1:8080` without changing the path.
Restart the development server after editing the proxy. See
[Angular proxy documentation](https://angular.dev/tools/cli/serve#proxying-to-a-backend-server).

A 401 from an unauthenticated protected endpoint is expected; the proxy does not
bypass authentication. Connection errors indicate the backend is unavailable or
the target port is wrong.

## Authentication

Registration posts email and password to `/api/v1/auth/register`, then redirects to
login with a success message. It does not automatically log in. New passwords must
contain at least 15 Unicode code points and no more than 72 UTF-8 bytes; spaces are
preserved. Login posts to `/api/v1/auth/login`.

The access token exists only in the auth service's memory. Reloads and new tabs
require login again; no local storage, session storage, cookies, or refresh tokens
are used. The bearer interceptor attaches it only to same-origin `/api/v1/`
requests, excluding login and registration.

The session ends at the earlier of JWT `exp` or the response's `expiresIn`.
A timer handles expiry while idle; guards and outgoing API requests also check the
clock in case browser timers were delayed. A protected request's 401 clears its
current session and returns to login; 403 preserves the session. A late response
from an earlier login or a rejected request from an older token cannot restore or
clear a newer session.

Logout clears memory and returns to login. It does not revoke a copied JWT on the
server: existing tokens remain valid until expiry (normally 15 minutes), including
after password, role, or account-status changes. There is no logout API.
Decoded roles only control navigation; backend JWT validation enforces access.
Return destinations are limited to the three workspace routes.

Forms prevent duplicate submission and show validation, credentials, duplicate
email, rate-limit, and connection errors without exposing backend details.

## My tasks

`/tasks` lists tasks assigned to the signed-in user through `GET /api/v1/tasks`,
including for ADMIN accounts. Pages contain up to 20 tasks, in the backend's order
(created newest first). Previous/Next navigation and a refresh action are available.

- Create a self-assigned task with a title, optional description and due date, and
  a status (To do, In progress, Done). Ownership is determined by the backend.
- Edit any of those fields; clearing description or due date sends explicit null.
- Complete a task with a status-only PATCH. Reopen it by editing its status.
- Delete after inline confirmation. Removing the final task on a page returns to
  the nearest remaining page.

Titles require a non-whitespace character and allow up to 200 Unicode code points;
descriptions allow 10,000. Supplied text is preserved. Due dates are calendar dates
without timezone conversion; past dates are allowed.

Loading, empty, success, and error states are shown. Failed saves retain the draft,
and server validation messages appear by the corresponding fields. Duplicate
writes are blocked while a request is pending. Writes are not automatically retried:
after a network failure, refresh the list to check whether the change was applied.
A successful write followed by a failed list refresh is reported separately.
401 responses use the existing session-expiry flow; permission, missing-task,
conflict, rate-limit, and server failures provide feedback.

## Build and verify

Run from `frontend/`:

```powershell
npm run build
npm test -- --watch=false
```

My tasks verification on 2026-09-29:

- Production build and 53 frontend tests passed.
- Task tests cover CRUD, pagination, validation, errors, bearer authentication,
  duplicate submission prevention, and cancellation on navigation.
- Live browser-to-backend task flows and visual review remain unverified.

Earlier authentication verification on 2026-09-29:

- Production build passed.
- 36 tests passed, covering forms, validation, guarded routing, roles, bearer scope,
  expiry, logout, 401/403 handling, and stale responses.
- Live browser authentication against the backend remains unverified.

Earlier scaffold verification on 2026-09-29:

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

Implement account administration and administrative task workflows behind ADMIN
guards, and verify the personal task flow against the live backend.
The first end-to-end milestone is registration, login, task creation/completion,
and logout through the browser.
