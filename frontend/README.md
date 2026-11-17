# Microshop frontend

Plain HTML, CSS and JavaScript (ES modules) with Bootstrap 5.3.9 from a CDN. No framework, no build step.

## Run it (development)

1. Start the backend (Docker stack or Eclipse) so the API gateway answers on `http://localhost:8080`.
2. Open the `frontend` folder in VS Code and click **Go Live** (Live Server extension).
3. Use **http://localhost:5501** only. `frontend/.vscode/settings.json` makes Live Server use `localhost`
   and port 5501 (its defaults are `127.0.0.1` and 5500; on this laptop 5500 is taken by Oracle's
   listener). The gateway allows only `http://localhost:5501` with credentials, and the refresh-token
   cookie only works there.

Live Server injects its own inline "auto reload" script. The Content-Security-Policy blocks inline scripts
on purpose, so auto reload does not work and the console shows a CSP warning for it: press F5 after a change.

## How it fits together

| File | What it does |
|---|---|
| `js/config.js` | API address, page size, cart limits |
| `js/api.js` | The only place that calls `fetch`: adds the token, refreshes once on 401, friendly errors |
| `js/auth.js` | Access token and current user in `sessionStorage` |
| `js/session.js` | Restore a session in a new tab, require login, log out |
| `js/ui.js` | Navbar, alerts, loading/empty states, formatting, confirmation dialog, paging |
| `js/cart-store.js` | The cart in `localStorage` |
| `js/pages/*.js` | One script per page |

Tokens: the access token (30 min) is in `sessionStorage`; the refresh token is an HttpOnly cookie that
JavaScript cannot read. Data from the API is always written with `textContent`, never `innerHTML`.

## Deploy (later)

nginx serves these files on the same origin as the API, so `API_BASE` in `js/config.js` becomes `""`,
the CSP's `connect-src` becomes `'self'`, the cookie is first-party and CORS is not needed.
