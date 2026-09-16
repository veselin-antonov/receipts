# ADR-0003 — JWT in an HttpOnly cookie, not a bearer token in storage

- **Status:** accepted
- **Date:** implemented early 2026, recorded 2026-09-16

## Context

The API needs stateless authentication for a browser SPA. The two usual options
are a bearer token the frontend stores and attaches to each request, or a token
the server sets as a cookie.

Recorded retroactively: this was already built and works. It is written down
because it drives non-obvious frontend behaviour that cost debugging time once
already.

## Decision

`POST /api/auth/token` takes HTTP Basic credentials and responds by setting an
RSA-signed JWT in a cookie that is `HttpOnly`, `Secure`, `SameSite=Strict`,
path `/`. `JwtCookieFilter` reads it back out on subsequent requests. The
frontend never sees or handles the token.

Active and inactive accounts get different token scopes and different lifetimes
(1 hour active, 15 minutes inactive).

## Why

- `HttpOnly` means a cross-site scripting bug cannot exfiltrate the token.
  `localStorage` offers no such protection.
- `SameSite=Strict` removes most cross-site request forgery exposure without a
  separate CSRF token scheme.
- The frontend gets simpler: no token plumbing, no refresh logic in the client,
  no storage to keep in sync across tabs.

## Consequences

Several of these are load-bearing and easy to get wrong:

- **Every frontend request must send same-origin credentials.** Forgetting this
  produces silent 401s that look like an expired session.
- **Session restore is a server round trip.** The frontend cannot inspect the
  cookie, so `GET /api/auth/status` is the only way to know whether a session is
  live after a page refresh. An earlier bug had this polling in a loop.
- **`403` from the login endpoint does not mean "wrong password".** It means the
  credentials were accepted and the account is unverified. The UI must
  distinguish this from a real auth failure or it will tell users the wrong
  thing.
- `Secure` means the cookie will not be set over plain HTTP, so local
  development and the deployed environment both need a working TLS or
  proxy story.
- Logout and token revocation do not exist. The cookie expires and that is all.
  Listed as deferred work in the roadmap.
