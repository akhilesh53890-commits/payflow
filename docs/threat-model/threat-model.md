# PayFlow Threat Model (STRIDE Methodology)

## 1. System Scope
PayFlow provides multi-tenant payment simulation and orchestration APIs, ledger accounting, webhook dispatch, and an administrative dashboard.

## 2. STRIDE Analysis & Mitigations

| Threat Category | Potential Threat | Impact | Technical Mitigation |
|---|---|---|---|
| **Spoofing** | Impersonation of merchant via stolen API key | Unauthorized payment/refund creation | API keys split into public key and secret key. Raw secret hashed with HMAC-SHA256 + pepper. Shown once. Constant-time comparison. |
| **Spoofing** | Forged JWT access tokens | Admin privilege escalation | Signed with HS256/RS256, strictly validated per request, short 15-minute TTL, rotating refresh tokens with DB revocation list. |
| **Tampering** | Mutation of financial ledger rows or payment history | Embezzlement, balance fraud | Immutable ledger rows; Flyway schema grants; no JPA update/delete methods exposed on `LedgerEntry`; DB balance derived from entry sum. |
| **Tampering** | Modifying request payload on retried idempotency key | Altering recipient or amount for duplicate charge | SHA-256 payload hash compared against stored hash; returns HTTP 409 Conflict upon mismatch. |
| **Repudiation** | Merchant claims they did not initiate refund or config change | Financial dispute | Append-only `audit_logs` table records actor ID, role, action, target entity, timestamp, IP, correlation ID, and metadata. |
| **Information Disclosure** | Leakage of API secrets, passwords, or card tokens in logs/metrics | Account takeover | Custom `MaskingPatternLayout` in Logback masks `Authorization`, `X-API-Key`, and sensitive card/secret fields. Entities never logged directly. |
| **Denial of Service** | Flooding payment API with identical or random requests | Resource exhaustion | Distributed rate limiting via Redis token bucket / sliding window; DB connection pooling bounds; request body size limits. |
| **Elevation of Privilege** | Merchant invoking internal admin endpoints (reconciliation run, system config) | System compromise | Spring Security method security (`@PreAuthorize("hasRole('ADMIN')")`), explicit role hierarchies, least-privilege token claims. |

## 3. Server-Side Request Forgery (SSRF) Defense for Webhooks
When dispatching outbound HTTP webhooks to merchant-registered endpoints:
- All target URLs are parsed and hostnames resolved to IPv4/IPv6 addresses.
- Blacklisted ranges:
  - `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16` (Private IPv4)
  - `127.0.0.0/8` (Loopback)
  - `169.254.0.0/16` (Cloud metadata service e.g. AWS 169.254.169.254)
  - `::1`, `fc00::/7`, `fe80::/10` (IPv6 Private/Link-local)
- Webhook dispatches to internal/loopback IPs are immediately blocked before socket creation.

## 4. Replay Attack Defense
- Webhooks contain a Unix timestamp and HMAC-SHA256 signature in `X-PayFlow-Signature`:
  $$v1 = \text{HMAC-SHA256}(\text{timestamp} + "." + \text{payload}, \text{merchantSecret})$$
- Receivers are instructed to reject signatures older than 300 seconds to prevent replay attacks.

