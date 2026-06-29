# Security Notes

## Browser/API boundary

- Production is designed for same-origin browser access through a reverse proxy.
- Development and staging use explicit origin allowlists only.
- Access tokens stay in browser memory.
- Refresh uses a host-only `HttpOnly` cookie plus double-submit CSRF protection.

## Response hardening

Backend responses now include:

- `Content-Security-Policy`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `X-Frame-Options: DENY`
- `Permissions-Policy`
- `Strict-Transport-Security` on secure transport

These headers apply to the Spring surface, including auth and administrator API responses.

## Observability and secret handling

- Prometheus metrics must not include token material, secrets, source bodies, or generated content payloads.
- Administrator diagnostics must expose counts and hold reasons only; raw credentials and worker tokens are excluded.
- Audit logs remain the system of record for security-relevant events such as login success, refresh rotation, replay detection, and logout.
