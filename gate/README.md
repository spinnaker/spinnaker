Spinnaker Gateway Service
------------------------------------
[![Build Status](https://api.travis-ci.org/spinnaker/gate.svg?branch=master)](https://travis-ci.org/spinnaker/gate)

This service provides the Spinnaker REST API, servicing scripting clients as well as all actions from [Deck](https://github.com/spinnaker/deck).
The REST API fronts the following services:
* [CloudDriver](https://github.com/spinnaker/clouddriver)
* [Front50](https://github.com/spinnaker/front50)
* [Igor](https://github.com/spinnaker/igor)
* [Orca](https://github.com/spinnaker/orca)

### Modular builds
By default, Gate is built with all authentication providers included. To build only a subset of
providers, use the `includeProviders` flag:
 ```
./gradlew -PincludeProviders=oauth2,x509 clean build
```
 You can view the list of all providers in `gradle.properties`.

### Edge authorization (identity tokens)

Gate is the authentication edge. After a caller authenticates (OAuth2/OIDC, SAML, LDAP, x509,
header, IAP, or basic), Gate resolves their roles locally via `kork-roles` and mints a signed
identity token (`kork-security`) that is propagated downstream in the `X-SPINNAKER-IDENTITY-TOKEN`
header. Role resolution stays local to the edge instead of relying on remote round-trips.

The token's `roles` claim is DEFLATE-compressed and base64url-encoded (`RoleClaimCodec`) so callers
in many groups don't push the propagated header past downstream HTTP header-size limits (e.g.
Tomcat's 8KB `max-http-header-size`, which otherwise rejects the request with a `400`).

Role sources stay pluggable and the policy backend is selectable through configuration:

| Key | Purpose | Default |
| --- | --- | --- |
| `auth.group-membership.service` | Selects the role provider: `file`, `ldap`, `github`, `google`, or `external` (trust the roles asserted by the authn mechanism, e.g. the OIDC `groups` claim). When unset, Gate trusts asserted roles only. | (unset) |
| `authz.roles.merge-external-roles` | Union provider roles with the roles asserted by the authn mechanism. | `true` |
| `authz.enabled` | Master switch for authorization (modeled on the legacy `services.fiat.enabled`). `false` disables authorization entirely (allow-all; absent/invalid tokens fall back to unsigned identity for audit only). `true` enforces authorization: a valid signed token is required and decisions are made against verified token roles. | `false` |
| `authz.token.issuer` / `authz.token.audience` / `authz.token.expiry` | Identity-token claims (`iss`/`aud`/`exp`). | see `SpinnakerTokenSettings` |
| `authz.signing.keys` | Ordered list of RSA signing keys (serialized JWK JSON, each with its private part and a unique `kid`). Shared across minters — the same key configured here for Gate and Front50 is used by both. **Required when `authz.enabled=true` (startup fails fast otherwise).** When empty and authorization is disabled, no signing key is created and Gate mints no identity tokens (downstream falls back to unsigned headers); configure keys to exercise the signed-token path. | (empty) |
| `authz.signing.active-key-id` | `kid` of the key in `authz.signing.keys` to sign with. When unset, the first key signs. Used to perform the cut-over step of a zero-downtime rotation (see below). | (first key) |
| `authz.gate.admin-roles` | Role names that grant the `admin` claim. | (empty) |
| `authz.gate.account-manager-roles` | Role names that grant the `account_manager` claim. | (empty) |
| `authz.gate.role-cache-ttl` | Short-TTL per-principal role cache window (absorbs high-volume API-token / per-request mint traffic). | `10m` |
| `authz.gate.role-cache-maximum-size` | Max principals held in the role cache. | `10000` |
| `authz.gate.pdp.backend` (`AuthzPolicyProperties`) | Policy Decision Point backend: default Spring-ACL PDP vs legacy permissions fallback. | see `AuthzPolicyProperties` |

API tokens (`spk_…`) perform an *edge token-exchange*: roles are resolved via `kork-roles` at
request time (served from the short-TTL role cache to absorb CI volume) and the identity token is
minted then. Gate's API-token Redis store is independent of any permission store and is retained.

#### Using an API token directly against a service (port-forward)

Not every downstream endpoint is exposed through Gate, so operators sometimes port-forward straight
to a service (Front50, Clouddriver, …). Those services only understand the *signed identity token*,
not the opaque `spk_` token. To let a single `spk_` token work everywhere, Gate exposes a
server-side token-exchange endpoint and the verifier services can call it transparently:

| Key | Purpose | Default |
| --- | --- | --- |
| `POST /auth/apiTokens/exchange` (Gate) | Resolves an opaque `spk_` token (in the JSON body `{ "token": "spk_…" }`) and returns `{ "identityToken": "<jwt>" }` — the same signed token Gate would have minted and propagated had the request gone through Gate. The token itself is the credential, so the endpoint is unauthenticated and returns `401` for an unknown/expired token. Active only when `api-tokens.enabled=true`. | — |
| `authz.api-token-exchange.enabled` (verifier services) | When `true`, a verifier service that receives a `spk_` token directly (in `X-Spinnaker-Token` or `Authorization: Bearer spk_…`) exchanges it once at Gate's endpoint and verifies the returned JWT through its normal identity-token path, so the resulting `SecurityContext` is identical to the Gate-proxied case. The normal hot path (a request already carrying `X-SPINNAKER-IDENTITY-TOKEN`) never triggers an exchange. The Gate to exchange against is the standard `services.gate.baseUrl`. | `false` |
| `authz.api-token-exchange.cache-ttl` | Upper bound on how long an exchanged JWT is cached per token (capped by the JWT's own `exp`), so repeated direct calls incur at most one round-trip per validity window. | `5m` |

`AllowedAccountsSupport` (the legacy `X-SPINNAKER-ACCOUNTS` header) is now derived from Clouddriver's
own role-filtered account listing (`GET /credentials`) rather than a replicated permission blob; the
authoritative account-authorization decision happens owner-locally in Clouddriver against the
caller's signed identity token.

#### Signing keys & zero-downtime rotation

There is a single shared signing-key set (`authz.signing.keys`) used by every minter — Gate (interactive
tokens) and Front50 (run-as tokens). There is no cryptographic need for separate keys: tokens are
distinguished by their claims, not their signature, so the same key can (and by default should) be shared.
The minter signs with one **active** key while the public halves of **all** configured keys are published
on each minter's JWKS endpoint (`GET /auth/jwks`), which is what makes rotation seamless:

1. **Add** the new key to `authz.signing.keys` alongside the current one and roll out. Every verifier now
   trusts both keys; tokens are still signed by the old key.
2. **Cut over** by setting `authz.signing.active-key-id` to the new key's `kid` and roll out. New tokens are
   signed by the new key; in-flight tokens signed by the old key still verify (it is still published).
3. **Remove** the old key from `authz.signing.keys` once the token validity window (`authz.token.expiry`,
   default 5m) has elapsed, and roll out.

With stable configured keys, ordinary restarts and scale-ups rotate nothing, so there is nothing to break.

### Debugging

To start the JVM in debug mode, set the Java system property `DEBUG=true`:

```
./gradlew -DDEBUG=true
```

The JVM will then listen for a debugger to be attached on port 8184.  The JVM will _not_ wait for
the debugger to be attached before starting Gate; the relevant JVM arguments can be seen and
modified as needed in `build.gradle`.
