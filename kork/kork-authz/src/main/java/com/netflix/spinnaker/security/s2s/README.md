# Service-to-service (s2s) authentication

Generic, mesh-wide authentication of the *calling service* on internal Spinnaker requests, plus a
**codified** per-endpoint authorization policy.

Two concerns are kept separate:

- **Authentication (config, generic):** how a peer's identity is read — CA/mTLS subject, service-mesh
  header, or Kubernetes ServiceAccount token. Selected per install via `authz.s2s.provider`.
- **Authorization (code, invariant):** which `SpinnakerService`s may call an endpoint, declared with
  `@AllowServiceCallers(...)` at the call site and reviewed in a PR. **Operators cannot widen it.**

Disabled by default (`authz.s2s.enabled=false`) — importing it changes nothing until an operator
opts in. When enabled, `@AllowServiceCallers` is always enforced (disallowed callers get 403); there
is no log-only mode.

## Operational requirement

When `authz.s2s.enabled=false` the `@AllowServiceCallers` aspect is **inert by design** — it is a
no-op, so annotated endpoints impose no service-caller restriction. Any production install that
enables authorization must therefore also set `authz.s2s.enabled=true`. Otherwise privileged
internal endpoints (e.g. the run-as initial mint) fall back to trusting any valid identity token,
bounded only by the pipeline's configured `runAsUser`, and Front50 must **not** be directly
user-reachable.

## Configuration reference (`authz.s2s`)

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `false` | Master switch. When true, policy is always enforced. |
| `provider` | `none` | `x509-subject` \| `header` \| `k8s-sa-token` \| `none`. |
| `service-name-prefix` | `spin-` | Deployment prefix stripped before matching the service enum. |
| `subject-regex` | `CN=([^,]+)` | (x509) capture group 1 = service name from the cert subject DN. |
| `header` | `X-Forwarded-Client-Cert` | (header) mesh-injected identity header; reads the XFCC `URI=` field. |
| `k8s.token-header` | `X-Service-Identity-Token` | (k8s) header carrying the projected token. Keep it outside `X-SPINNAKER-*`. |
| `k8s.token-path` | `/var/run/secrets/spinnaker/identity/token` | (k8s) **calling side**: this pod's audience-bound projected token. |
| `k8s.token-refresh-seconds` | `60` | (k8s) how often the token file is re-read, so in-place rotation is picked up. |
| `k8s.namespace` | `spinnaker` | (k8s) required namespace of calling ServiceAccounts. |
| `k8s.audiences` | — | (k8s) expected `aud` — **required**; the resolver is disabled without it. |
| `k8s.jwks-uri` | `https://kubernetes.default.svc/openid/v1/jwks` | (k8s) JWKS verifying presented tokens; the default needs no RBAC. |
| `k8s.jwks-ca-cert-path` | `/var/run/secrets/kubernetes.io/serviceaccount/ca.crt` | (k8s) CA for the JWKS endpoint's certificate; empty = JVM default trust store. |
| `k8s.jwks-token-path` | `/var/run/secrets/kubernetes.io/serviceaccount/token` | (k8s) credential for the JWKS fetch; empty = fetch anonymously. |
| `k8s.issuer` | — | (k8s) expected `iss` (optional; frequently *not* the in-cluster URL). |
| `k8s.jws-algorithms` | `[RS256]` | (k8s) accepted token signature algorithm(s), e.g. `[ES256]`; empty falls back to `RS256`. |
| `k8s.jwks-connect-timeout-ms` | `2000` | (k8s) connect timeout for fetching the JWKS; `0` = no timeout. |
| `k8s.jwks-read-timeout-ms` | `2000` | (k8s) read timeout for fetching the JWKS; `0` = no timeout. |
| `k8s.jwks-size-limit-bytes` | `51200` | (k8s) max accepted JWKS response size; `0` = no limit. |

## CA / mTLS (the common case) — `spinnaker-local.yml`

One CA for the whole install, one leaf cert per service (CN = service name). `${spring.application.name}`
gives each service its own identity from a single shared block.

```yaml
server:
  ssl:
    enabled: true
    key-store: /etc/spinnaker/certs/${spring.application.name}.p12
    key-store-password: changeit
    key-store-type: PKCS12
    trust-store: /etc/spinnaker/certs/ca.p12
    trust-store-password: changeit
    trust-store-type: PKCS12
    client-auth: need          # gate should use `want` (mixed user + service traffic)

ok-http-client:
  keyStore: /etc/spinnaker/certs/${spring.application.name}.p12
  keyStorePassword: changeit
  keyStoreType: PKCS12
  trustStore: /etc/spinnaker/certs/ca.p12
  trustStorePassword: changeit
  trustStoreType: PKCS12
  refreshableKeys:
    enabled: true              # hot-reload rotated certs

authz:
  s2s:
    enabled: true
    provider: x509-subject
    subject-regex: "CN=([^,]+)"
```

Point inter-service URLs at https (host must match the server cert SAN):

```yaml
services:
  orca:    { baseUrl: https://spin-orca:8083 }
  echo:    { baseUrl: https://spin-echo:8089 }
  front50: { baseUrl: https://spin-front50:8080 }
```

### cert-manager (auto-issued, auto-rotated leaf certs)

```yaml
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: spin-orca
spec:
  secretName: orca-mtls
  issuerRef: { name: spinnaker-ca, kind: Issuer }   # the one CA
  commonName: orca                                   # CN -> SpinnakerService.ORCA
  dnsNames: [ spin-orca, spin-orca.spinnaker.svc ]
  keystores:
    pkcs12:
      create: true
      passwordSecretRef: { name: keystore-pass, key: password }
```

## Kubernetes ServiceAccount tokens (no certificates)

Each service must run under its own ServiceAccount (`spin-orca`, `spin-echo`, …). Mount an
audience-bound projected token; the identity is the ServiceAccount, so all replicas resolve
identically.

```yaml
# pod spec
volumes:
  - name: spin-identity-token
    projected:
      sources:
        - serviceAccountToken:
            audience: spinnaker
            expirationSeconds: 3600
            path: token
```

```yaml
# spinnaker-local.yml
authz:
  s2s:
    enabled: true
    provider: k8s-sa-token
    k8s:
      namespace: spinnaker
      audiences: [ spinnaker ]   # required
```

That is the whole configuration: `jwks-uri` and the CA and credential paths already default to what
every pod has. **No RBAC needs to be granted.** Kubernetes ships a
`system:service-account-issuer-discovery` ClusterRoleBinding for the `system:serviceaccounts` group,
so every ServiceAccount may already read the JWKS; the verifying service authenticates that fetch
with its own token and trusts the endpoint via the projected cluster CA.

The caller attaches the token file's contents on the `X-Service-Identity-Token` header. The name is
deliberately outside the `X-SPINNAKER-*` namespace, which `AuthenticatedRequestFilter` copies into
the MDC wholesale — a bearer credential named `X-SPINNAKER-*` would be written to logs and
re-attached to the receiving service's own outbound calls.
This is done by `ServiceIdentityInterceptor` (see `client/`), which services add to the OkHttp
clients that target other Spinnaker services. It is not a global customizer: that would also attach
the credential to artifact fetches and other third-party calls. A new internal client that hits an
`@AllowServiceCallers` endpoint has to opt in explicitly.

### Offline verification, and what it costs

Tokens are verified **offline** against the cached cluster JWKS. Kubernetes offers a stronger
alternative in the
[TokenReview API](https://kubernetes.io/docs/reference/kubernetes-api/definitions/token-review-v1-authentication/),
which validates a token's *bound* claims against live cluster state — offline verification
[cannot](https://kubernetes.io/docs/reference/access-authn-authz/service-accounts-admin/), so a token
bound to a Pod stays valid for its full TTL even after that Pod is gone, and there is no revocation
path for a leaked one.

That trade is deliberate. `tokenreviews` is a non-namespaced resource, so it can only be granted
through a *ClusterRoleBinding* to `system:auth-delegator`; requiring cluster-scoped RBAC in order to
install a namespace-scoped application is too heavy a precondition for an open-source deployment,
while offline verification needs no RBAC at all. The compensating control is a short projected-token
TTL — `expirationSeconds: 3600` above is load-bearing, not cosmetic, and shorter is better.
Kubernetes rotates projected tokens automatically before expiry, so a short TTL costs nothing
operationally. If your threat model needs bound-claim validation, prefer `x509-subject` or a service
mesh rather than widening RBAC.

The JWKS fetch is bounded by the `jwks-*-timeout-ms` / `jwks-size-limit-bytes` settings so a slow or
hostile endpoint cannot stall resolution, which runs synchronously on request threads.

### When every caller is denied

`Denied caller <no authenticated caller>` while the peer *is* sending a token means verification
failed. The verifying service logs the reason at `ERROR`; in order of likelihood:

- **JWKS not retrievable.** Logged as "failed to retrieve JWKS". A TLS handshake failure means
  `jwks-ca-cert-path` is not the CA that signed the endpoint's certificate — the API server's is the
  cluster CA, which the JVM does not trust by default. A `401` means `jwks-token-path` is unreadable
  and the cluster runs with `--anonymous-auth=false`. Both defaults are correct for an in-cluster
  API server; they only need changing when `jwks-uri` points somewhere else.
- **Audience mismatch.** The caller must present a token from a dedicated projected volume whose
  `audience` matches `k8s.audiences`, not the default API-server-audience token.
- **`issuer` does not match the token.** If you set it, it must equal the token's `iss`, which is the
  cluster's configured issuer URL — often *not* `https://kubernetes.default.svc`. Leave it unset
  unless you have checked.

## Service mesh (Istio/Linkerd)

The sidecar terminates mTLS and injects the peer identity; the app reads it:

```yaml
authz:
  s2s:
    enabled: true
    provider: header
    header: X-Forwarded-Client-Cert
```

## Codified policy (not configurable)

```java
@PostMapping("/auth/runAsToken")
@AllowServiceCallers({SpinnakerService.ECHO, SpinnakerService.ORCA})
public RunAsTokenResponse mintRunAsToken(...) { ... }

@PostMapping("/auth/issueExecutionToken")
@AllowServiceCallers(SpinnakerService.ORCA)
public RunAsTokenResponse issueExecutionToken(...) { ... }
```

Adding a new privileged caller is a code change (PR-reviewed), never a config edit.
