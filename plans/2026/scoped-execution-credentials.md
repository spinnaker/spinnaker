# Scoped execution credentials for remote job callbacks, and a distributed bake model

Status: **analysis / scoping** — no implementation has started. This document captures the problem,
the current state of the relevant subsystems, a proposed architecture, a security analysis, and a
phased roadmap for future PRs.

## 1. Problem

Today, when orca runs a remote container (e.g. via `RunJobStage` on Kubernetes), that container gets no
Spinnaker identity at all. It cannot:

1. Fetch the context/parameters of the specific pipeline execution + stage that spawned it,
2. Post output back to that stage on completion in a way that resumes the pipeline, or
3. Fetch the artifacts that execution has declared, for local initialization.

The immediate output of a job is only visible to Spinnaker via polling (orca/clouddriver querying job
status from the outside), and there is no notion of a credential scoped narrowly enough to hand to an
untrusted, ephemeral, remote process — every credential mechanism in the platform today is either a full
user/service-account identity (broad, long-lived) or nothing at all.

The motivating end goal is a **distributed bake system that conceptually replaces centralized
Packer/rosco baking**: ephemeral remote containers perform templating/bake operations themselves,
pulling bake parameters and input artifacts through a scoped credential and pushing the resulting
artifact (AMI id, rendered manifest) back into the running stage — instead of requiring an always-on,
centrally-polled rosco service to do the work and track its state.

This document scopes what it would take to add that capability, evaluates prior art (including a
previous attempt at "remote baking" in the rosco project itself), and lays out a security analysis and
phased roadmap.

## 2. Current state

### 2.1 Gate — authentication and token issuance

Gate has a new (2026) opaque API-token subsystem in
`gate/gate-web/src/main/java/com/netflix/spinnaker/gate/security/apitoken/`:
`ApiTokenController`, `ApiTokenService`, `ApiTokenAuthenticationFilter`, `RedisApiTokenRepository`,
`ApiTokenAuthConfigurerAdapter`. Tokens are opaque, `spk_`-prefixed, SHA-256 hashed, and stored in Redis.
Principal types are `USER` or `SERVICE_ACCOUNT`; on authentication the token resolves to a **cached Fiat
`UserPermission.View`** — the token itself carries no scope or claims, only identity, and all
authorization is delegated to Fiat's role model at request time. Expiry is day-granularity only
(90/365-day max) — there is no per-resource-instance scoping mechanism anywhere in gate today.

`ApiTokenAuthConfigurerAdapter` (`gate/gate-web/src/main/java/com/netflix/spinnaker/gate/security/apitoken/ApiTokenAuthConfigurerAdapter.java:44-59`)
establishes gate's pattern for bolting on a new principal type: a dedicated, `SessionCreationPolicy.STATELESS`
Spring Security filter chain, matched only against token-bearing requests, ordered ahead of the SSO/header/
x509 chains. This is the pattern a new capability-token filter should follow.

Two other pieces of in-tree prior art matter for the design below:
- `gate-iap`'s `IapAuthenticationFilter` already depends on `nimbus-jose-jwt` (declared in
  `gate/gate-iap/gate-iap.gradle`) and does the "parse `SignedJWT`, verify against a `JWK`, extract claims,
  build an authentication token" flow — real, in-tree JWT *verification* prior art.
- `kork-github`'s `GitHubAppAuthenticator` is in-tree prior art for JWT *minting*: short-lived (10-minute)
  signed JWTs, a `PrivateKey` loaded from PEM, per-key striped locking to avoid duplicate mint races.
- `kork-secrets` (`SecretManager`, engines in `kork-secrets-{aws,gcp,k8s}`) is gate's existing mechanism
  for resolving `encrypted:...` config values from a real secret store — the right place to source a new
  JWT signing key, not a bespoke keystore.

### 2.2 Fiat — authorization model

`Permissions`/`Authorization` (READ/WRITE/EXECUTE/CREATE) is a resource-**type** + resource-**name** +
verb model (`fiat/fiat-core/.../model/{Authorization,resources/Permissions}.java`), evaluated via
`FiatPermissionEvaluator` and `@PreAuthorize` SpEL, caching a `UserPermission.View` per principal.
Resource types are coarse and relatively static: `APPLICATION`, `ACCOUNT`, `SERVICE_ACCOUNT`, etc. There
is **no per-instance resource type** — no notion of "this one pipeline execution" as an authorizable
resource — and `ServiceAccount` (`fiat/fiat-core/.../model/resources/ServiceAccount.java`) is just a name
plus `memberOf` roles, with no inherent scoping field of its own.

This model is built for coarse, low-cardinality, mostly-static role grants. It is the wrong shape for
millions of ephemeral, per-execution grants — extending it with an `EXECUTION` resource type is explicitly
**not** recommended (see §3.1).

### 2.3 Orca — execution/stage APIs and the resume mechanism

`PATCH /pipelines/{id}/stages/{stageId}` (`orca/orca-web/src/main/groovy/com/netflix/spinnaker/orca/controllers/TaskController.groovy:624-630`)
is confirmed, directly, to do `stage.context.putAll(context)` — an **unscoped** merge of an arbitrary
context map, with no key filtering today. It delegates to
`CompoundExecutionOperator.updateStage` (`orca/orca-core/src/main/java/com/netflix/spinnaker/orca/pipeline/CompoundExecutionOperator.java`),
which does locking/retry and then calls `runner::reschedule` — a **push**-based, immediate stage resume
(not poll-based). This is the real building block for "external system posts output, pipeline resumes."

`orca-remote-stage` (`StartRemoteStageTask`/`MonitorRemoteStageTask`/`RemoteStageExtensionService`,
marked `@Beta`) is existing prior art for exactly this pattern: an external system does work and writes
back a narrow `remote.status`/`remote.result` pair via the same PATCH endpoint;
`MonitorRemoteStageTask` polls only those two context keys to complete the stage. It does *not* do
generic context merging on the read side — this narrow, namespaced-write convention is the one to copy
for the new scoped endpoint, precisely because the general PATCH endpoint is unscoped.

`RunJobTask.groovy` (`orca/orca-clouddriver/src/main/groovy/com/netflix/spinnaker/orca/clouddriver/tasks/job/RunJobTask.groovy`)
injects no identity into the pods it creates today. `onCancel` (confirmed at lines 55 and 61-62,
`jobUtils.cancelWait(stage)`) is an existing synchronous hook that already runs at job teardown — the
natural place to wire credential revocation, with no new orchestration needed.

`ArtifactResolver` (`orca/orca-core/src/main/java/com/netflix/spinnaker/orca/pipeline/util/ArtifactResolver.java`)
is the only place in the stack that knows which artifacts belong to a given execution
(`resolveExpectedArtifacts` against `expectedArtifacts`/trigger artifacts). It is internal only — not
REST-exposed today.

### 2.4 Clouddriver — artifact fetch has no authorization today

`PUT /artifacts/fetch` (`clouddriver/clouddriver-web/src/main/java/.../controllers/ArtifactController.java:76-90`)
streams artifact content given a full `Artifact` object plus credentials. **Neither this endpoint nor
gate's pass-through of it carries any `@PreAuthorize` check today** — any authenticated principal that can
construct an `Artifact` reference can fetch it. This is a real, present gap, not a hypothetical, and it
directly shapes the design of the new scoped artifact-fetch path (§3.1, point 2): a thin proxy in front of
an unauthorized endpoint is still unauthorized.

### 2.5 Rosco — two bake paths, one reusable seam

VM/Packer baking (`BakeStage`/`CreateBakeTask`/`MonitorBakeTask`/`CompletedBakeTask` in `orca-bakery` →
rosco's v1 `BakeryController` → `CloudProviderBakeHandler` → `JobExecutorLocal`, a local subprocess) is
stateful and polling-based, tracked in a Redis-backed `BakeStore` with a background `BakePoller`. There is
exactly one `JobExecutor` implementation shipped in rosco itself (local subprocess exec) — no
pluggable/remote backend.

Manifest/Helm baking (`rosco-manifests` module, `V2BakeryController`, `BakeManifestService` per renderer,
e.g. `HelmBakeManifestService`) is **already architecturally separate**: fully synchronous request/response,
no `BakeStore`, no polling. This is the natural, lower-risk seam to extract as a standalone distributable
operation, because it doesn't require untangling any of rosco's stateful bake-tracking machinery.

### 2.6 Prior art considered and rejected: `spinnaker/rosco`'s `rosco-remote` module

The upstream `rosco-remote` module (`spinnaker/rosco`, path `rosco-remote/`, archived December 2025) was
fetched and read directly (`io.armory.spinnaker.rosco.jobs.{k8s,fargate}`) to check whether it already
solves this problem. It doesn't, and understanding precisely why is useful:

**It is a `JobExecutor` swap, not a distributed execution engine.** Both `K8sRunJobExecutor` and
`FargateJobExecutor` are `@Primary` implementations of the *same* `JobExecutor` interface
`JobExecutorLocal` implements — they only change *where the packer subprocess runs*. Rosco itself remains
the single always-on orchestrator: it still owns `BakeStore`, still runs `BakePoller`, and still does
100% of result-retrieval by directly polling the target platform's own APIs (Kubernetes Job/Pod status for
the k8s executor; ECS task status plus CloudWatch Logs via `AWSLogsClient`/`GetLogEventsRequest` for the
Fargate executor). **There is no push-back/self-report channel from the job to Spinnaker at all** — this
module offers no prior art for the "post results back" half of the desired design, and it does nothing to
remove rosco as a stateful bottleneck/single point of failure.

**Credential handling is ad hoc, per-executor, and instructive as a cautionary example:**
- `K8sRunJobExecutor` maps job parameters directly to **plaintext pod environment variables**
  (`aws_access_key`→`AWS_ACCESS_KEY_ID`, `aws_secret_key`→`AWS_SECRET_ACCESS_KEY`,
  `aws_session_token`→`AWS_SESSION_TOKEN`) — real cloud credentials land in the pod spec/env, visible via
  `kubectl describe pod`, inherited by child processes, and likely to end up in crash dumps. It also
  mounts *all* of rosco's local `configDir` (every packer template, for every provider) as a single shared
  `ConfigMap` in a hardcoded `rosco-jobs` namespace, used by every job — no per-job or per-execution
  isolation of configuration data.
- `FargateJobExecutor` explicitly sets `taskRoleArn(null)` — deliberately opting **out** of Fargate's
  native per-task IAM role mechanism — and instead has rosco itself call `sts.assumeRole(...)`, then
  relay the resulting temporary AWS credentials into the task via a bespoke, single-purpose broker: a
  HashiCorp Vault "2x-use token," written once by rosco (`writeJobContextToVault`) and consumed once by
  the task's `fargate-rosco-command-wrapper.sh` wrapper script at startup. This is structurally close in
  *shape* to a capability token (short-lived, limited-use, job-scoped session name
  `"armory-ami-bake" + jobId`) — but it is Vault-specific, hardcoded to this one executor, requires Vault
  as an additional operational dependency, and, critically, brokers **cloud-provider** credentials rather
  than a **Spinnaker-side callback capability** — conflating two concerns this design deliberately keeps
  separate (see §3.3, Phase 4).

**Conclusion:** `rosco-remote` is evidence against reusing either its code or its credential patterns, and
it pre-answers the question "why not just swap rosco's `JobExecutor` for a remote one?" — that approach
was tried, shipped, and abandoned without solving statefulness, push-back, or credential scoping.

## 3. Proposed architecture

### 3.1 Core credential + API layer (shared by every transport model)

**1. A capability JWT, not a Fiat extension.** Gate mints a short-lived (minutes, not days), signed JWT
whose claims embed `executionId`, `stageId`, the allowed operations (read-context / post-output /
fetch-artifacts), and `audience=gate`. Verification is structural — signature plus claims matched against
the request's path parameters — via a new, dedicated Spring Security filter chain mirroring
`ApiTokenAuthConfigurerAdapter`'s established pattern, producing a distinct `Authentication` type that is
explicitly **not** routed through `FiatPermissionEvaluator`/`UserPermission.View`. This is a capability
token, not an identity: no roles, no `memberOf`, nothing `PermissionService` or front50 needs to know
about. The signing key is sourced via `kork-secrets`; signing/verification reuses the already-present
`nimbus-jose-jwt` dependency and the `GitHubAppAuthenticator`/`IapAuthenticationFilter` patterns already
in-tree. This directly avoids `rosco-remote`'s mistake of brokering credentials through a one-off,
executor-specific side channel — one mint/verify mechanism, reused by every consumer.

*Why not extend Fiat instead?* Fiat's model is built for coarse, low-cardinality, mostly-static role
grants. A new `EXECUTION` resource type would mean millions of ephemeral, per-execution `Permissions`
entries flowing through a cache designed for a small, slowly-changing set of applications/accounts/roles —
the wrong shape entirely. Keeping the capability token structurally separate (verified locally by
signature and claim-matching, no Fiat round trip) also keeps it out of the hot, high-cardinality path,
which matters once distributed bakes are running at any real volume.

**2. A new, narrow, execution+stage-scoped REST surface in orca**, separate from `TaskController`/
`PipelineController`:
- `GET` context for a single stage only.
- `POST` output — a narrow, namespaced write (copying `orca-remote-stage`'s `remote.status`/
  `remote.result` convention rather than the unscoped `putAll`), internally reusing
  `CompoundExecutionOperator.updateStage`/`runner::reschedule` for the actual resume. The scoping has to
  be enforced in a new thin wrapper around `updateStage`, since that method is intentionally generic and
  authz-agnostic.
- `GET` artifacts / artifact content — this must live in **orca**, not gate, and must validate the
  requested artifact reference against `ArtifactResolver`'s resolved set for that specific execution
  *before* proxying to clouddriver, using orca's own existing service-to-service credential — never
  forwarding the ephemeral capability token downstream. Because clouddriver's fetch endpoint has no
  authorization of its own (§2.4), a thin pass-through here would just relocate the existing hole; real
  per-execution filtering is required. This bounds a compromised token's blast radius to "artifacts this
  execution already declared," not "everything clouddriver has credentials for."

This is also the first real push-back channel Spinnaker will have had for remote work — `rosco-remote`
had none at all.

**3. Revocation.** Expiry (minutes) is the backstop; a Redis `jti` deny-list — reusing the `JedisPool`
already wired for `RedisApiTokenRepository`, no new infrastructure — is the enforcement, checked in the
verification filter. Wire eager revocation into two hook points that already exist and already fire,
rather than inventing new orchestration:
- `RunJobTask.onCancel` (cancellation path — already runs at job teardown).
- `ExecutionComplete`/`StageComplete`/`TaskComplete` events (normal-completion path — closes the more
  common case; a stage that finishes successfully still has a live token until natural expiry unless
  revoked eagerly here).

Consider single-use semantics specifically on the POST-output claim (a deny-list entry written on first
successful use) — read operations can remain valid for the full window, but a "post final output" action
should not be replayable.

### 3.2 Execution/transport models: two options, not strictly sequential phases

The credential+API layer above is transport-agnostic. There are two materially different ways work
actually reaches a remote worker, and they serve different deployment topologies — this section documents
both rather than assuming "spin up a Kubernetes job" is the only shape.

**Option A — Orchestrator-provisioned ephemeral compute (`RunJobStage`).** Orca itself creates the
compute (a Kubernetes Job today; conceptually ECS/Fargate or similar later) and mints + injects a
capability token at creation time, scoped 1:1 to that exact pod. Injection should be a mounted Secret
**file**, not an environment variable — env vars leak via `kubectl describe pod`, child-process
inheritance, and crash dumps, which is precisely the mistake `K8sRunJobExecutor` made with raw AWS
credentials (§2.6) — and should be scoped to the job container only, relevant once/if `RunJobStage` grows
sidecar support.

Strong properties: orca knows exactly which image/pod spec it created (provenance), revocation ties
cleanly to pod teardown (`RunJobTask.onCancel`), and no new standing infrastructure is required.

Structural limitation: bounded to environments orca's own service credentials can already provision
into — the same limitation `rosco-remote` had, since it needed direct Kubernetes/ECS/Vault credentials on
the rosco side. This does not help on-prem workers, third-party build farms, air-gapped environments, or
contributor-operated compute that Spinnaker's control plane has no provisioning access to.

**Option B — Long-lived worker pool, pub/sub-distributed work.** Pre-registered, long-lived worker
processes subscribe to an operation-request topic and need only **outbound** connectivity to a message
broker — no inbound exposure, and no orchestrator-side provisioning credentials into the worker's
environment at all. This is the architectural opposite of Option A's limitation, and is worth designing
for explicitly rather than treating as a hypothetical, because it ties directly into work already in
flight elsewhere in this codebase:

- Orca/gate publishes a **job announcement** — `executionId`, `stageId`, operation type; deliberately
  low-sensitivity, no secret material — onto a topic using **Single**-delivery (competing-consumer,
  exactly-once-across-fleet) semantics, never Broadcast, which would hand a copy to every subscribed
  worker. This maps directly onto the Broadcast+Single delivery design currently being built in
  `kork-pubsub` (see the project memory on kork pub/sub, and `plans/2026/pub-sub-support.md` as tracked on
  the `korkPubsubRedis`/`korkPubsubBroadcast` branches — not yet merged to `main` as of this writing). It's
  a natural integration point rather than new messaging infrastructure, and it's also the reason MQTT
  specifically should not be assumed as the only transport: `kork-pubsub`'s backend-pluggable design
  (Redis Streams today, SNS/SQS/Kafka under assessment) could carry the same announce/claim contract
  without committing to one broker technology.
- The worker does **not** receive the capability JWT via the broker message directly. Instead it exchanges
  the announcement for a token via a dedicated gate "claim" endpoint, authenticated by the worker's **own**
  standing identity. This is a genuinely new identity type — distinct from both today's Fiat users/service
  accounts and the ephemeral per-execution capability token — a "registered worker" credential scoped only
  to "subscribe to announcement topics" and "exchange an announcement for a capability token," nothing
  else. It needs its own registration/rotation/revocation lifecycle; this is new administrative surface,
  not a footnote (see §4).
- Once claimed, the worker uses the resulting capability token against the **same** REST surface from
  §3.1 (context/output/artifacts) — the credential+API layer is fully shared between both options; only
  how the worker learns about the job, and how it authenticates its own standing identity, differs.

**Recommendation:** treat Option A as the near-term, lower-risk path — it needs no new broker dependency
and reuses this repo's own established patterns end-to-end (§2.1, §2.3) — and Option B as a parallel track
worth designing for now, because it solves a class of deployment (untrusted network position, no
control-plane provisioning access into the worker's environment) that Option A structurally cannot, and
because `kork-pubsub`'s Single-delivery mode is already being built for an unrelated reason (immediate
clouddriver account-refresh notifications). This would be a second, validating consumer of that work
rather than a reason to build new messaging infrastructure from scratch.

### 3.3 Distributed bake as the first real consumer

Built on Option A first: a new stage/job-runner image receives its token via §3.2's pod injection, fetches
bake parameters and input artifacts via §3.1's scoped REST surface, and runs the **extracted/shared**
`rosco-manifests` renderer logic (e.g. `HelmBakeManifestService`) locally. Manifest/Helm bakes are the
first target since they're already stateless/synchronous with no `BakeStore` to untangle (§2.5) — and
unlike `rosco-remote`'s approach, there is no shared cluster-wide `ConfigMap`: templates are fetched
per-execution via the scoped artifact endpoint instead.

Packer/VM-image bakes are a harder, later phase, gated on a separate per-cloud pod-IAM design — explicitly
**not** `rosco-remote`'s approach of the orchestrator brokering assumed-role credentials through Vault
(§2.6). The capability token in this design covers the Spinnaker-side callback only; cloud-provider
image-publish credentials are a distinct, orthogonal concern that deserves its own follow-up design
(likely IRSA/Workload-Identity-style native per-pod cloud identity, not a custom broker). Option B applies
here too, longer term, for bake farms Spinnaker's control plane has no provisioning access to.

## 4. Security analysis

Beyond what's already covered in §3.1 (revocation/deny-list) and noted inline above, the following are
first-class concerns for this design — several sharpened directly by what `rosco-remote` got wrong:

- **Credential material must never be raw cloud-provider secrets in the job spec/env.** `rosco-remote`'s
  `K8sRunJobExecutor` (§2.6) is the cautionary example: plaintext AWS credentials as pod env vars. This
  design's capability token must be file-mounted and must never itself be, or unlock, a general
  cloud-provider credential — it authorizes callbacks to Spinnaker only.
- **Unauthorized-by-default downstream endpoints are a present gap, not a hypothetical.** Clouddriver's
  `PUT /artifacts/fetch` (§2.4) has no `@PreAuthorize` today. Any proxy built on top of it must do real
  per-execution filtering via `ArtifactResolver`, not a thin pass-through, which would just relocate the
  existing hole.
- **Broadcast vs. Single delivery is a security choice, not only a performance one (Option B).** Job
  announcements must use `kork-pubsub`'s Single/competing-consumer mode; Broadcast would hand a copy of the
  announcement to every subscribed worker in the fleet, expanding blast radius from "one worker" to "the
  whole pool."
- **The broker is a new trust boundary (Option B).** Whichever backend carries announcements — an MQTT
  broker, Redis Streams via `kork-pubsub`, or a future SNS/SQS/Kafka backend — needs mTLS between all
  parties, strict per-topic/per-client ACLs (no wildcard subscriptions), and its own audit logging. A
  compromised or misconfigured broker can let an unauthorized subscriber claim jobs or observe
  announcements it shouldn't see. Because `kork-pubsub` is explicitly backend-pluggable, this posture
  varies by backend and must be documented per-backend rather than assumed uniform.
- **Long-lived worker identities are a bigger standing liability than per-execution ephemeral tokens
  (Option B).** A compromised worker credential persists until manually rotated/revoked and can
  potentially claim many jobs over time, unlike a `RunJobStage` pod's token, which dies with the pod.
  Mitigate with narrow worker scope (topic-subscribe + announcement-claim only — never Fiat
  application/account access), short rotation windows, and per-worker volume/anomaly monitoring. Worker
  registration and offboarding is genuinely new administrative surface that doesn't exist in Spinnaker
  today and should be scoped as such.
- **Announce-then-exchange needs its own replay/race protection (Option B).** Two workers could race to
  exchange the same announcement if delivery/ack semantics ever cause redelivery; the exchange endpoint
  must be atomic/idempotent (first successful claim wins) — Single-delivery's competing-consumer semantics
  help but don't fully guarantee this on their own, since ack failures can still cause redelivery.
- **Provenance/attestation is weaker in Option B than Option A.** In Option A, orca directly created, and
  therefore knows, the exact pod spec/image digest that ran. In Option B, the orchestrator only knows "some
  currently-authorized worker" handled the job — the worker's own identity/attestation is what's trusted,
  not something Spinnaker observed at creation time. This is a real trust-model trade-off with direct
  bearing on audit/compliance posture for anything regulated, such as image provenance for production
  bakes, and should be stated plainly rather than glossed over.
- **No-inbound-network-required is a genuine security *win* for Option B, worth naming as such.** The
  worker only needs egress to the broker and to gate — a materially smaller attack surface than Option A's
  requirement that orca hold direct, standing provisioning credentials into every target environment (the
  same requirement that made `rosco-remote`'s Fargate/Kubernetes executors need broad cloud credentials on
  the rosco side in the first place).
- **Token replay within the validity window.** Even with a `jti` deny-list, nothing prevents concurrent use
  from two places until the first legitimate consumption is recorded. Single-use semantics on stateful
  claims (POST-output specifically) close this; reads can remain multi-use within the window.
- **Multi-container pods / shared node leakage (Option A).** If `RunJobStage` ever grows sidecar support,
  the token must be scoped to a single container via volume-mount visibility/`securityContext`, not
  broadcast to the whole pod spec by default.
- **Log scrubbing.** No existing precedent was found for scrubbing header/claim values in gate's request
  logging. Given the unusually tight expiry on these tokens compared to today's day-granularity API
  tokens, accidental logging is a real (if lower-severity) exposure risk — flag as a gap to close, not a
  safeguard to lean on.
- **Clock skew.** Minute-granularity expiry means clock skew between gate (verifier) and wherever a token
  is minted/used matters more than it does for today's day-granularity tokens; a small (30-60s) leeway in
  verification is standard for most JWT libraries but should be called out explicitly here given how tight
  the expiry is.

## 5. Phased roadmap

Each phase below is intended to be independently mergeable and testable without the phases after it being
"real" yet — Phase 0 has no caller, Phase 1 has no injector, Phase 2 has no consumer — matching this
project's preference for landing independent work ahead of coupled work.

- **Phase 0 — Gate token mint/verify infrastructure.** JWT signing/verification via `nimbus-jose-jwt`
  (already present via `gate-iap`) and `kork-secrets` for key sourcing; new dedicated Spring Security
  filter chain plus a distinct `Authentication` type, modeled on `ApiTokenAuthConfigurerAdapter`; a mint
  endpoint callable only by already-privileged service principals; a Redis `jti` deny-list reusing the
  existing `JedisPool`. No consumers yet — independently reviewable and testable in isolation.
- **Phase 1 — Orca scoped REST surface.** GET context, POST output (narrow namespaced write via
  `CompoundExecutionOperator.updateStage`), GET/stream artifacts (via the `ArtifactResolver`-validated
  proxy to clouddriver). Auth checks the capability token's claims directly, not Fiat SpEL.
- **Phase 2 — `RunJobStage` token injection + revocation wiring (Option A).** Mint-and-mount a token into
  the job pod as a file; wire revocation into `RunJobTask.onCancel` and normal-completion events.
- **Phase 3 — Distributed manifest/Helm bake via Option A.** The first real end-to-end consumer; requires
  extracting/sharing the `rosco-manifests` renderer logic so it's consumable by both rosco's existing
  `V2BakeryController` path and the new remote container image, rather than reimplementing it.
- **Phase 4 — Distributed Packer/VM-image bake.** Harder; gated on a separate per-cloud pod-IAM design
  (not a Vault-style credential broker) before scoping further. Treat as its own follow-up document.
- **Phase 5 — Option B worker registration/claim model (parallel track).** Depends on `kork-pubsub`'s
  Single-delivery mode landing on `main` (tracked at `plans/2026/pub-sub-support.md` once merged).
  Initially scoped as an alternative bake-worker transport for environments Option A can't reach.
- **Phase 6 — Rosco deprecation path.** A decision/communication milestone, not code: once distributed
  manifest bakes are proven in production (and ideally VM-image bakes have a validated design), define
  what — if anything — rosco continues to own (e.g. template/recipe validation, local dev/test bakes)
  versus what moves fully to the distributed model.

## 6. Open questions / follow-ups

- The per-cloud pod-IAM design needed for Phase 4 is explicitly out of scope here and needs its own doc.
- Whether single-use semantics are needed beyond the POST-output claim.
- Whether multi-container `RunJobStage` pods are on any near-term roadmap — affects how much to design
  for token-to-single-container scoping now versus later.
- For Option B specifically: the worker registration/offboarding UX, whether MQTT or `kork-pubsub`'s
  Redis/future SNS-SQS backends should be the reference transport, and how worker-identity credentials
  get issued in the first place. Flag for a dedicated follow-up once Phases 0-3 validate the core model.
