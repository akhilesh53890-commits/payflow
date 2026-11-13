# ADR-007: Security Architecture and Threat Mitigation

## Status
Accepted

## Context
Payment infrastructure represents a prime target for credential stuffing, key compromise, race-condition exploits, replay attacks, Server-Side Request Forgery (SSRF) via webhooks, and injection attacks.

## Decision
1. **Authentication & Authorization**:
   - Platform dashboard users authenticate using BCrypt password hashing, short-lived JWT access tokens (15-minute validity), and rotating refresh tokens (7-day validity) stored hashed in PostgreSQL with revocation lists.
   - Role-Based Access Control (RBAC) with least-privilege roles: `ADMIN`, `MERCHANT`, `CUSTOMER`.
2. **Merchant API Key Security**:
   - API keys are issued with a public identifier (e.g., `pk_live_...` or `pk_test_...`) and a secret key (`sk_live_...` / `sk_test_...`).
   - The raw secret is **shown only once** upon generation in the UI/API response.
   - The database stores only the HMAC-SHA256 hash salted with an application pepper:
     $$\text{StoredHash} = \text{HMAC-SHA256}(\text{RawSecret}, \text{Pepper})$$
   - Secret tokens, card numbers, and credentials are never logged (`Filter` masks authorization headers and sensitive JSON fields).
3. **Webhook Security & SSRF Protection**:
   - Outbound webhooks sign the payload using HMAC-SHA256 with the merchant's webhook secret, including a timestamp in the signature header:
     `X-PayFlow-Signature: t=1699999999,v1=abcdef...`
   - **SSRF Defense**: Before dispatching an HTTP POST to a merchant-registered webhook URL:
     - The domain is resolved via DNS.
     - Any destination resolving to private/reserved IP blocks (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `127.0.0.0/8`, `169.254.0.0/16`, `::1`) is rejected with `SSRF_DETECTED` to prevent internal infrastructure scanning.
4. **Data Sanitization & Injection Prevention**:
   - Parameterized SQL via Spring Data JPA and Hibernate.
   - Strong DTO validation using Jakarta Validation (`@Valid`, `@NotNull`, `@Pattern`, `@Positive`).
   - Secure HTTP response headers (HSTS, CSP, X-Content-Type-Options, Frame-Options).

## Consequences
- **Positive**: Hardened against OWASP Top 10 API vulnerabilities, SSRF, replay attacks, and key exposure.
- **Positive**: Complies with modern zero-trust enterprise security standards.

