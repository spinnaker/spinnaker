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
previous attempt at "remote baking" in the rosco project itself, and external workload/CI engines), and
lays out a security analysis and phased roadmap.

A related question, addressed in §3.4, is whether the same mechanism generalizes beyond baking to a
broader "pluggable operations" capability — e.g. letting a remote job execute alternative logic for a
specific cloud-provider operation (an EC2 deploy, say) instead of clouddriver's built-in implementation.

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

### 2.7 Prior art: existing workload/CI engines vs. building a bespoke remote-runner protocol

Before committing to a bespoke announce/claim/exchange protocol for Option B (§3.2), it's worth asking
whether an existing, mature workload-distribution engine should be adopted instead of inventing a new
wire protocol — consistent with this project's general preference to check for existing adapters before
building custom transport plumbing (the same question was already asked, and answered case-by-case, while
designing `kork-pubsub`'s delivery semantics). Three systems were considered, each representing a
different family:

**Temporal** (descended from Uber's Cadence) is a durable workflow-execution engine whose Worker model is
structurally very close to what Option B needs: long-lived Worker processes long-poll (pull, not push) a
named Task Queue for work, execute Activities (units of work — e.g. "call the cloud provider API"), and
report results back to the Temporal server, which then resumes the workflow — requiring only outbound
connectivity from the worker, exactly Option B's design goal. Temporal also has native **Signals**,
letting an external process asynchronously inject data into a running workflow — directly analogous to
"post output back to resume the stage." Its Task Queue + Namespace model gives work-partitioning/routing
for free. The catch: Temporal's own authentication (typically mTLS per namespace/client) is coarser than
this design's per-execution capability JWT — it authenticates a worker to a namespace/queue, not to one
specific job's narrow scope — so §3.1's capability-token layer would still be needed on top of Temporal
for the actual gate/clouddriver callback surface, even if Temporal replaced the raw task-distribution
transport.

*Product-level impact, not just a technical fit question.* Adopting Temporal is not a transport swap in
isolation — it's a new mandatory-or-optional piece of infrastructure operators would need to run and
manage, on top of a platform (Spinnaker) already notorious for operational complexity. Concretely: Temporal
is itself new stateful infrastructure (a persistence store — Cassandra/MySQL/PostgreSQL — plus additional
server components in self-hosted mode, or a recurring SaaS cost/vendor dependency if Temporal Cloud is used
instead); it adds a new external dependency to Spinnaker's own compatibility/upgrade matrix (client SDK
versions, server version compatibility, its own CVE surface to track); and if it's optional, that's a
second worker-transport implementation to build and maintain indefinitely alongside `kork-pubsub`, not a
replacement for it. Any recommendation to adopt Temporal needs to weigh this operational/maintenance cost
explicitly against the transport benefits above, not just the technical fit.

*Gaps this roadmap does not address, and should not be assumed to.* This document does not propose
adopting Temporal, and nothing here closes gaps in orca's own execution engine — `orca-queue`'s
durability/retry semantics, whatever they are today, are unaffected. Adopting Temporal (if ever pursued)
would not, by itself, give orca workflow-versioning/determinism guarantees, multi-region workflow
execution, or any of Temporal's other durable-execution features for pipelines generally — only for the
narrow worker-transport slice described in §3.2, if that slice is built at all. Most importantly: **Temporal's
own client SDKs and worker runtime have zero built-in knowledge of Spinnaker** — they know nothing about
executions, stages, tasks, artifacts, or Fiat. All of the Spinnaker-specific integration work (claiming a
job, exchanging for a capability token, calling the context/output/artifact endpoints in §3.1) would still
have to be hand-written by this project regardless of whether Temporal is adopted — none of it comes "for
free" with the engine.

**FluxCD** represents a fundamentally different philosophy: pull-based GitOps reconciliation. An
in-cluster controller continuously watches a source of truth (a git repo or OCI artifact) and reconciles
live state toward it — there is no "dispatch an operation and await a result" protocol at all, because no
operation is dispatched, only desired state read and continuously applied. This repo already has an
internal analog to that philosophy: **`keel/`**, Spinnaker's own declarative delivery service, built
around continuous reconciliation of resources toward a desired spec. The relevant lesson here:
reconciliation is a genuinely different paradigm from pipelines' imperative, ordered-stage execution
model, and doesn't map cleanly onto "execute this operation, then this one" pipeline semantics — so it
isn't a drop-in alternative to the capability-token/callback design for pipeline-driven work. It is,
however, a legitimate longer-term alternative specifically for §3.4's "plugin-like alternative operation
logic" idea below: a remote reconciler could watch for desired-state declarations and apply them
independently, rather than being invoked via an imperative per-execution RPC.

*Gaps this roadmap does not address.* This document does not propose adopting FluxCD as infrastructure —
it's cited purely as a precedent for a different philosophy, not a dependency to add. None of Flux's
actual capabilities (multi-cluster fleet management, drift detection/alerting, image automation, Kustomize/
Helm-release reconciliation) are in scope here, because this doc's operations aren't general Kubernetes-
resource reconciliation. Equally important: this roadmap does **not** extend, modify, or fix `keel` in any
way — it's cited purely as an internal architectural analog to Flux's philosophy, and any of keel's own
existing gaps or limitations are untouched by anything in this document. And as with Temporal: Flux's own
controllers (`source-controller`, `kustomize-controller`, etc.) have no Spinnaker awareness whatsoever —
this is not a system whose workers could pick up a Spinnaker-scoped operation without fully custom
integration code.

**Woodpecker CI** (a Drone CI fork) and the broader family it represents — Drone itself, GitLab Runner,
GitHub Actions' self-hosted runners (already cited in §2.1 as prior art for the capability-token concept
itself) — validate Option B's core shape as proven and widely deployed: long-lived Agents register with a
central server using an agent credential, long-poll for pending work (gRPC, in Woodpecker/Drone's case),
execute it in an isolated container, and stream status/results back over the same channel — no inbound
network exposure required. Their authentication model is instructive by contrast, not by imitation: a
single shared agent secret (or, in newer versions, a per-agent token) authorizes an agent broadly — once
trusted, it can pick up and execute *any* queued work item, not one pre-scoped item. This is coarser than
what this design proposes: agent/worker identity is standing and broad, while the individual *work item's*
credential is narrow and ephemeral. That distinction is deliberate — a bad CI build step and a bad EC2
deploy do not carry the same blast radius, and §3.4/§4 lean on that distinction directly.

*Gaps this roadmap does not address.* No CI-pipeline UI, artifact caching, build-matrix support, or plugin
marketplace is in scope or implied by referencing these systems — they are cited purely to validate the
"long-lived agent pulls work, executes, reports back" shape, not as candidates for adoption. And again:
none of Woodpecker's, Drone's, GitLab Runner's, or GitHub Actions' existing agent/runner binaries have any
Spinnaker awareness — reusing any of them as-is is not an option; a fully custom agent speaking this
design's capability-token protocol would still be required.

**A recurring theme across all three systems, worth stating once, plainly: none of their native
worker/agent/controller processes integrate with Spinnaker today, and none would without this project
writing that integration itself.** Adopting any of these systems — for transport, for philosophy, or for
validation — does not reduce the amount of Spinnaker-specific glue code this design requires; at most it
changes what the low-level "how does a worker learn about work and send a heartbeat" plumbing looks like.
The capability-token issuance/verification, the context/output/artifact REST surface, and the
claim-then-exchange pattern in §3.2 are Spinnaker-specific by necessity and are not something any of these
products provide.

**Conclusion:** none of these systems should be adopted wholesale in place of this design. Temporal's
worker/task-queue/signal model is the strongest candidate specifically for Option B's transport layer, if
and when Option B is built, but §3.1's capability-token layer remains necessary regardless of transport
choice — none of these systems provide fine-grained, per-operation authorization scoped to a single
execution/stage/task out of the box. FluxCD/`keel`-style reconciliation is worth keeping in mind as an
alternative shape for §3.4's plugin-operation idea specifically, longer-term, rather than for the
pipeline-driven baking use case this doc otherwise focuses on.

### 2.8 Echo — existing eventing infrastructure (directly relevant, previously uncited)

Before treating Option B's transport (§3.2) or CDEvents-style triggering (§3.5) as greenfield, it matters
that **echo** — Spinnaker's own event-routing service — already has real, production infrastructure in
both areas:

- **Pub/sub pipeline triggers already exist and are multi-backend.** `echo-pubsub-core` is a
  provider-agnostic core (`PubsubMessageHandler`, `PubsubSubscribers`, `PollingMonitor`), with concrete
  subscriber implementations in `echo-pubsub-google` (Google Cloud Pub/Sub, `GooglePubsubMonitor`/
  `GooglePubsubSubscriber`), `echo-pubsub-aws` (SNS/SQS, `SQSSubscriber`/`SQSSubscriberProvider`), and a
  Kafka subscriber in `echo-pubsub-core` itself (`KafkaMonitor`/`KafkaSubscriber`). Authentication is
  provider-IAM-based, not an app-level shared secret — Google subscriptions use a service-account JSON key
  (optional; subscriptions can be public per an explicit code comment), AWS subscriptions rely on
  topic/queue ARN + IAM. Message schema is Spinnaker-proprietary/pluggable (Jinja templates for
  GCS/GCR/GCB, or a `CUSTOM` mode), not CDEvents/CloudEvents by default. **This must be evaluated before
  building new `kork-pubsub`-based infrastructure for Option B** (§3.2) — either as something to extend, or
  something this design needs to explicitly reconcile with, so the platform doesn't end up with two
  parallel eventing subsystems solving adjacent problems.
- **A CDEvents/CloudEvents-typed webhook endpoint already exists in production**: `POST
  /webhooks/cdevents/{source}` (`echo/echo-webhooks/src/main/groovy/com/netflix/spinnaker/echo/controllers/WebhooksController.groovy:137-158`),
  which deserializes an `io.cloudevents.CloudEvent` request body and routes it into echo's normal pipeline-
  trigger flow (`event.details.type = "cdevents"`). This materially changes §3.5's framing: the question
  isn't "should Spinnaker adopt CDEvents," it already has a foothold — the real question is whether to
  build on/extend this existing route for operation-triggering, and how it should relate to the
  capability-token claim-then-exchange pattern this design requires (§3.5 already flags that CDEvents alone
  carries no such authorization semantics; that applies here too, including to this specific endpoint).
- **No signature/HMAC/shared-secret verification exists on any of echo's webhook endpoints today**,
  including the CDEvents route — confirmed directly (no `secret`/`hmac`/`signature` handling found in
  `echo-webhooks`). The generic webhook endpoints and per-repo SCM handlers (`GithubWebhookEventHandler`,
  `BitbucketServerEventHandler`, etc.) parse payload shape but verify no signature; any protection today
  would have to come from network/gateway controls, not application-level verification. This is a present
  gap, not a hypothetical, in the same category as clouddriver's unauthenticated artifact-fetch endpoint
  (§2.4) — and it matters directly if §3.5's notification model is ever built on this existing surface.

**Igor was also checked and found not directly relevant.** Igor's CI-source integrations
(`JenkinsBuildMonitor`, `TravisBuildMonitor`, `GitlabCiBuildMonitor`, `ConcourseBuildMonitor`, all
extending `CommonPollingMonitor`) are exclusively **poll-based** — igor holds outbound credentials to each
configured CI system and polls it; there is no inbound "external CI system authenticates to igor" pattern
to draw on here, unlike echo's webhook/pub-sub surfaces above.

### 2.9 SPIFFE/SPIRE — workload identity, evaluated as external prior art

SPIFFE (Secure Production Identity Framework For Everyone) defines SPIFFE IDs
(`spiffe://trust-domain/path`) and SVIDs (SPIFFE Verifiable Identity Documents — X.509 or JWT) as a
standard workload-identity format. SPIRE is the reference implementation: a Server plus per-node Agents;
Agents perform **workload attestation** (verifying a process's node/pod/namespace/service-account/image
attributes via plugins — e.g. a Kubernetes attestor calling the Kubelet API) and issue short-lived,
auto-rotated SVIDs to attested workloads via a local **Workload API** (a Unix domain socket) — the workload
pulls its own identity; nothing is pushed into the pod spec. SPIRE supports **federation** across trust
domains (multiple clusters/clouds), with documented patterns for exchanging an SVID for cloud-IAM
credentials (conceptually similar to IRSA).

A repo-wide search (source, Gradle build files, config/properties, `*.md` docs, across every service) found
**zero references to SPIFFE, SPIRE, or SVID anywhere in this codebase**, and no in-house equivalent —
`clouddriver-kubernetes`'s only identity model is standard kubeconfig/ServiceAccount-token auth *to* the
K8s API, not verification *of* the pods it creates. Unlike §2.8's echo findings, which uncovered existing
internal capability, this is external prior art only — SPIFFE/SPIRE would be a fully greenfield addition,
in the same category as §2.7's Temporal/FluxCD/Woodpecker evaluation, not something to build on directly.

**Product-level impact.** SPIRE Server is new stateful infrastructure (SQLite for small/single-node
deployments, MySQL/PostgreSQL for HA) plus a per-node Agent DaemonSet — a real new operational dependency,
the same category of cost already named for Temporal in §2.7. Two things differentiate it, though, and are
worth stating explicitly rather than treating identically:
- **Outage blast radius is smaller and more graceful.** If SPIRE Server/Agents go down, already-issued
  SVIDs remain valid until their short TTL expires — new attestation stops, but nothing analogous to "all
  in-flight workflows stall" (Temporal's failure mode) happens immediately.
- **A meaningful subset of operators may already run SPIRE for other reasons** — service-mesh mTLS (e.g.
  Istio integrates with SPIRE) or general platform workload identity. That makes this potentially an
  "integrate with what already exists" proposition for some deployments, unlike Temporal, which would be
  net-new infrastructure for essentially everyone. This asymmetry doesn't change the bottom-line
  recommendation (below), but it's a materially different adoption calculus worth naming.

**Gaps not addressed.** SPIRE does not solve Phase 4's cloud-provider-credential problem by itself — though
SVID-to-cloud-IAM federation patterns exist and are worth keeping in mind as a possible future unification
point, not a solved problem here. And, matching §2.7's recurring theme: SPIRE workloads/agents have zero
built-in Spinnaker awareness — adopting it strengthens authentication, it does not reduce the
Spinnaker-specific integration work described in §3.1.

**Conclusion:** the strongest candidate for strengthening Option A's (and potentially Option B's) workload
authentication if this is ever pursued, but it does not replace §3.1's capability-token layer any more than
Temporal replaces it for Option B's transport — see §3.6 for how the two would fit together.

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

Worth noting as complementary, not competing, prior art: Kubernetes' own audience-bound, auto-rotated
projected ServiceAccount tokens (the `TokenRequest` API) could strengthen this further — a relying party
can verify, via the cluster's own OIDC issuer, that a request genuinely originates from a pod the cluster
believes exists, independent of orca's own bookkeeping. This wouldn't replace the capability JWT (a K8s SA
token carries no `executionId`/`stageId` claims), but layering it underneath as an additional
authentication factor is worth a line in a future design pass rather than being decided here. This is the
Kubernetes-only, no-new-infrastructure version of a broader idea — §2.9/§3.6 evaluates SPIFFE/SPIRE as its
cross-platform, federation-capable generalization, relevant for deployments spanning more than one cluster
or compute platform.

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

See §2.7 for an evaluation of existing workload/CI engines (Temporal, FluxCD, Woodpecker/Drone) as
potential alternatives to building a bespoke announce/claim protocol for Option B — the short version:
Temporal's worker/task-queue/signal model is the strongest transport candidate if Option B is pursued,
but it does not replace §3.1's capability-token layer, which none of the evaluated systems provide at
the same granularity.

A WebSocket/STOMP-based broker transport, and a hybrid model blending Option A and Option B (orchestrator-
provisioned compute that then behaves like a temporary Option B worker), were both raised as directions
worth keeping in mind. Both are explicitly deferred — see §7.

Before designing any new broker/transport plumbing for Option B, §2.8 needs to be resolved: echo already
runs production Google Pub/Sub, SNS/SQS, and Kafka subscribers, and a `kork-pubsub`-based announcement
mechanism will either need to extend that existing infrastructure or explain why it doesn't, rather than
standing up a second, unrelated eventing path in the same platform.

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

### 3.4 Generalizing beyond baking: pluggable remote operation execution

The mechanism proposed above — a scoped capability credential plus a narrow context/output/artifact
callback surface — is not inherently specific to baking. The same primitives generalize to letting a
remote job execute **any** Spinnaker operation using alternative logic — e.g. a custom, non-clouddriver-native
implementation of "deploy to EC2," selected per-operation rather than compiled permanently into
clouddriver. This is a materially bigger and higher-stakes change than distributed baking (see §4), and
clouddriver's current architecture needs to be understood before scoping it further.

**How clouddriver executes operations today.** `POST /{cloudProvider}/ops`
(`clouddriver/clouddriver-web/.../controllers/OperationsController.groovy`) resolves each operation
description to an `AtomicOperationConverter` via `AtomicOperationsRegistry` — in the current scheme,
`AnnotationsBasedAtomicOperationsRegistry` finds Spring beans annotated with a provider-specific
annotation (e.g. `@AmazonOperation("createServerGroup")`) already compiled into clouddriver's own
classpath; there is no dynamic/plugin-jar-loaded equivalent. `OperationsService.collectAtomicOperations`
(`clouddriver-core/.../orchestration/OperationsService.java`) converts the description — resolving and
attaching account credentials via `AccountCredentialsProvider` at this stage — and produces an
`AtomicOperation`. `DefaultOrchestrationProcessor.process()`
(`clouddriver-core/.../orchestration/DefaultOrchestrationProcessor.java`) then creates a `Task`
(`TaskRepository`) and — inside its own internal thread pool, in the same clouddriver JVM/pod that
received the request — calls `atomicOperation.operate(priorOutputs)` **synchronously and in-process**.
The caller (orca's `KatoService`) polls `GET /task/{id}` for status/results: the same "orchestrator polls
a task record" shape already seen in rosco's `BakeStore`/`BakePoller` and `RunJobStage`'s
`WaitOnJobCompletion` — a recurring pattern in this codebase that the new scoped-callback mechanism is
well positioned to replace with a push model, consistently, everywhere it appears.

**No existing extension point covers this.** Clouddriver's only `kork-plugins`/PF4J extension points
today are `GlobalDescriptionValidator` and `UserDataTokenizer` (both in `clouddriver-api`) — narrow,
cross-cutting hooks that *augment* validation/templating, not seams for replacing an operation's actual
execution logic. There is no `SpinnakerExtensionPoint` for `AtomicOperation`, `AtomicOperationConverter`,
or `CloudProvider` itself. "Alternative logic scoped to the operation" is therefore not something
clouddriver's existing plugin architecture already supports — it would be a genuinely new mechanism, not
an application of an existing seam.

**Proposed shape.** Rather than modifying `DefaultOrchestrationProcessor`'s core dispatch loop (a bigger,
riskier change touching task lifecycle and the instance-affinity/graceful-shutdown semantics built on
`Task.getOwnerId()`), the lower-risk starting shape mirrors distributed bake directly: introduce a new
`AtomicOperation` implementation per remote-capable operation type (e.g. a hypothetical
`RemoteEc2DeployAtomicOperation`, registered exactly like any other operation via the existing
annotation-based registry), whose `operate()` method — instead of performing the cloud-provider call
itself — mints a capability token scoped to that specific `Task`/operation instance (a `Task`-scoped
variant of the same credential design in §3.1, since clouddriver's `Task` is the equivalent unit here, not
an orca stage), dispatches to a remote worker via either Option A or Option B, and awaits the result via
the same context/output/artifact-style callback surface. Because `AccountCredentials` resolution already
happens at the converter layer, independent of the `AtomicOperation` implementation
(`AbstractAtomicOperationsCredentialsSupport`), this shape can reuse clouddriver's existing
credential-resolution path up to the point of actually calling the cloud provider's API — at which point
it hits the same open problem already flagged for distributed Packer bakes (§3.3, Phase 4): the remote job
needs real cloud-provider credentials to make the mutating API call, a separate, harder problem than the
Spinnaker-side capability token, and one that now applies to *any* remote-executed operation, not just VM
image builds. A more invasive, longer-term alternative — a formal `OperationExecutor` SPI that
`DefaultOrchestrationProcessor` dispatches to instead of always calling `.operate()` in-process, mirroring
rosco's own `JobExecutor` abstraction (§2.5) — would generalize this across many operation types without a
bespoke `Remote*` class per operation, but is a bigger core-clouddriver change and should be scoped as its
own follow-up once the narrower per-operation shape is validated.

**A genuine operational upside worth naming.** Today, an in-flight operation is tied to the lifecycle of
the specific clouddriver pod that accepted it (`Task.getOwnerId()`, used for graceful shutdown of
in-flight tasks on that instance). Moving execution to a remote job insulates long-running mutating
operations from clouddriver pod restarts/redeploys — a real resilience win, independent of the
"pluggability" motivation.

### 3.5 Notification-style triggering: CDEvents as an alternative to direct invocation

Everything in §3.1-§3.4 assumes a **direct-invocation** model: Spinnaker (orca/gate) decides an operation
needs to happen and dispatches it — either by creating compute (Option A) or by publishing a targeted job
announcement a worker claims (Option B). Worth naming as a distinct alternative shape: **notification-style
triggering** via [CDEvents](https://cdevents.dev), the Continuous Delivery Foundation's specification for
a standard vocabulary and CloudEvents-based envelope for CI/CD lifecycle events (pipeline-run, task-run,
build, artifact — including artifact-packaged/published — and environment/service events). Spinnaker is
itself a CDF project, which makes this a natural question to ask rather than an arbitrary one — and, per
§2.8, not a purely hypothetical one: echo already exposes `POST /webhooks/cdevents/{source}`, deserializing
`io.cloudevents.CloudEvent` bodies into its pipeline-trigger flow. The question this section is really
asking, then, is not "should Spinnaker adopt CDEvents" but **whether operation-triggering should build on
that existing route** rather than inventing a separate mechanism — with the caveat, below, that the
existing route currently carries none of the authorization semantics this design requires.

Under this shape, Spinnaker publishes a standardized CDEvents event (e.g. a task-run "queued" event
carrying identifying detail, but not the capability token itself) onto a bus, and *any* appropriately
configured external system — not necessarily one running Spinnaker-aware worker code at all — can react to
it and independently perform the work (e.g. run a bake using entirely its own tooling, outside Spinnaker's
control), then emit its own CDEvents completion event (e.g. artifact-packaged) that **echo** — Spinnaker's
existing event-routing service, the natural in-repo integration point here, the same way `keel/` is the
natural analog for §2.7's FluxCD discussion — consumes to resume the pipeline.

This is a genuinely different relationship than either option in §3.2: it decouples "who is allowed to act
on this notification" from Spinnaker's own worker-registration model entirely, in exchange for standard,
vendor-neutral interoperability with the wider CD ecosystem. A CDEvents-consuming system elsewhere in an
org's toolchain could react to a Spinnaker bake-request event with zero Spinnaker-specific integration
work, and vice versa. It's also a direct answer to §2.7's recurring gap — "none of these systems'
workers integrate with Spinnaker" — since a CDEvents-native system doesn't need a Spinnaker-specific agent
at all, only a CDEvents listener it may already have.

**Important limits.** CDEvents is a *notification schema and envelope*, not a work-distribution or
authorization protocol. It says nothing about claim-exclusivity — nothing stops the same event being acted
on by zero, one, or many listeners unless the transport underneath enforces single delivery (the same
Broadcast-vs-Single question already raised in §3.2/§4) — and it carries no notion of the capability-token
scoping this entire design is built around. This is not abstract: echo's existing `/webhooks/cdevents/{source}`
route (§2.8) has no signature/HMAC verification today, so as it stands it cannot be the entry point for
anything that triggers a mutating operation without first closing that gap. A pure "fire a CDEvents event
and hope something completes it" model would be strictly weaker on authorization/provenance than either
Option A or Option B as designed. If pursued, CDEvents should be layered *on top of* the existing
claim-then-exchange pattern (§3.2): the event payload carries a claim-check reference, not raw context or
the token itself, and a listener still has to authenticate and exchange for a capability token via gate
before it can read stage context, fetch artifacts, or post output — Option B's model, with CDEvents
providing a standardized announcement format in place of a bespoke JSON schema. Framed this way, CDEvents
is best understood as a possible **event vocabulary choice for Option B's announcement**, not a fourth
architecture that bypasses §3.1's authorization layer.

**Lower-risk, complementary recommendation.** Independent of whether CDEvents is ever adopted as a
trigger mechanism, having echo emit CDEvents-formatted lifecycle notifications (operation
requested/started/completed) purely for observability/interop — *alongside*, not instead of, the
capability-token invocation flow — is a safe, low-risk way to make this work visible to CDEvents-aware
tooling elsewhere in an organization's stack, without weakening any of the authorization model above. Given
§2.8's finding, this is now a concrete, scoped starting point rather than a from-scratch integration:
review whether `WebhooksController`'s existing CDEvents plumbing can be reused for *emission*, not just
ingestion, before building anything new. Small and independent enough to scope on its own, and does not
depend on Option A/B or the pluggable-operations work in §3.4 landing first.

This section is exploratory, not a roadmap commitment: no phase in §5 currently includes CDEvents work.

### 3.6 SPIFFE/SPIRE as a unifying identity substrate for Option A and Option B

§2.9 evaluates SPIFFE/SPIRE as external prior art; this section works out how it would actually fit this
design, if pursued. The core reframe is the same one already applied to every other external system in
this doc: SPIRE is an **authentication** layer, not an authorization layer. A SPIFFE ID identifies a
*class* of workload (namespace/service-account/image) — not one ephemeral job's specific
executionId+stageId+allowed-operations scope. §3.1's capability-token layer remains required on top
regardless of whether SPIRE is adopted, exactly as §2.7 already concludes for Temporal and Option B's
transport.

**For Option A, attestation replaces injection entirely, not just the injection method.** §3.2 already
recommends mounting the capability token as a Secret file rather than an env var, to limit
`kubectl describe`/crash-dump/child-process leakage. SPIRE goes further: because the workload pulls its own
SVID from a local Workload API socket, attested transparently by the node-local Agent, there is no
Secret-in-pod-spec step at all for the *authentication* half of the problem — nothing is pushed into the
pod spec for SPIRE to work. The capability/scope credential itself would still need to be delivered
somehow: either still injected as today, or obtained via an SVID-authenticated call to gate that exchanges
the workload's now-verified identity for a scoped capability token — structurally similar to Option B's
claim-then-exchange pattern (§3.2), just with SPIRE doing the identity half instead of a registered-worker
credential.

**The unifying point, and the most significant reason this is worth a dedicated subsection rather than a
footnote under Option A:** SPIRE attests both Option A's ephemeral, orca-provisioned pods and Option B's
long-lived, pre-registered workers through the same trust domain and Workload API — different attestation
plugins/registration entries, one identity substrate. That means SPIRE's own registration tooling
(`spire-server entry create` and friends) could substitute for the bespoke worker-registration/rotation/
revocation lifecycle §4 currently flags as novel administrative surface Spinnaker would otherwise have to
build from scratch for Option B. This is a possible mitigation path worth naming, not a decided design —
it still requires operators to run SPIRE, and doesn't change that Option B's claim-then-exchange pattern
and single-delivery announcement semantics (§3.2, §4) are needed regardless of which system authenticates
the worker's standing identity.

**Concrete technical note.** JWT-SVIDs, not X.509-SVIDs, are the natural fit here — X.509-SVIDs imply an
mTLS architecture change to gate, a much bigger lift than this design calls for. Verifying a JWT-SVID is
structurally the same "parse `SignedJWT`, verify against a JWK/trust bundle" flow already cited as in-tree
prior art in §2.1 (`IapAuthenticationFilter`, `nimbus-jose-jwt`) — gate-side verification code is largely
reusable, not novel, if this is pursued.

**Must remain optional**, the same zero-external-dependency-baseline principle applied throughout this
document (§3.1's capability token works with no external infrastructure; `kork-pubsub`'s backends are
pluggable; Temporal was evaluated but not mandated). Not every Spinnaker deployment runs Kubernetes, let
alone SPIRE — this would be a strongly-recommended optional enhancement for operators who have or are
willing to run it, never a hard requirement of §3.1's baseline design.

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
  today and should be scoped as such. If SPIFFE/SPIRE is adopted (§3.6), this specific burden could be
  substantially reduced by using SPIRE's own registration tooling instead of building this lifecycle from
  scratch — a possible mitigation path, not assumed or designed here.
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
- **Pluggable remote operation execution (§3.4) raises the stakes well beyond baking, and should be gated
  accordingly.** Baking produces an artifact that still has to pass through a normal, reviewed deploy
  stage before it can affect live infrastructure. A remote-executed *deploy/resize/terminate* operation
  mutates production state directly and immediately. Every concern already listed above for distributed
  bake and Option B (credential handling, provenance/attestation, worker-identity risk) applies here with
  meaningfully higher consequence on failure. Recommend explicitly restricting mutating remote operations
  to **Option A only** (orchestrator-provisioned, provenance-known compute) until Option B's provenance
  and worker-vetting questions are actually answered — do not extend the long-lived, third-party-worker-pool
  model to mutating cloud operations as a first step.
- **This is a new, first-party-code-only trust surface today, and should stay that way unless deliberately
  changed.** Clouddriver's `AtomicOperation` implementations are currently 100% first-party code compiled
  into clouddriver, reviewed through this repo's normal process — there is no existing plugin/extension
  point that lets alternative operation logic run with real infrastructure credentials (§3.4). Any
  "plugin-like" remote-operation capability should be scoped, from day one, to logic the platform operator
  has explicitly configured/trusted per account or operation type — not to arbitrary user-supplied code —
  to avoid inadvertently building a code-execution-as-a-service surface with cloud credentials attached.
  Note that `kork-plugins` does have real, existing plugin distribution/versioning infrastructure
  (`PluginInfoRelease`, a front50-backed remote release cache — confirmed directly in the codebase) that
  could plausibly be reused for *distributing* trusted remote-operation logic even though its runtime
  extension points don't cover `AtomicOperation` (§3.4). That infrastructure's trust model rests on the
  front50 registry being trusted, not on cryptographic signing — no signature/checksum verification was
  found for plugin binaries — so it does not, by itself, close this gap.
- **Echo's existing webhook surface (§2.8), including its CDEvents route, has no signature/HMAC
  verification today.** If §3.5's notification-triggering idea is ever built on `WebhooksController`'s
  existing infrastructure, this is a present gap to close first, not a hypothetical — identical in kind to
  clouddriver's unauthenticated artifact-fetch endpoint (§2.4), and higher-stakes here since a webhook can
  originate operation-triggering activity rather than just reading data.
- **Provenance/attestation, referenced above, has existing standards worth building toward rather than
  inventing from scratch.** SLSA and in-toto are the established supply-chain frameworks for attesting
  build/artifact provenance, directly applicable to what distributed bake (§3.3) produces (an AMI or
  rendered manifest). This also intersects concretely with work already in flight in this repo: the
  per-service CycloneDX SBOM effort (PR #8019, `SpinnakerSbomPlugin`) is a natural companion to attaching
  provenance attestation to remotely-baked artifacts, and should be coordinated with rather than duplicated
  if this design is pursued.
- **No trace-context propagation is designed here, and should be.** Nothing in this design currently
  carries distributed-tracing context (e.g. W3C Trace Context) across the Spinnaker-to-remote-worker
  boundary. Without it, a distributed bake or remote operation becomes a black box from an observability
  standpoint the moment it leaves orca/clouddriver — undermining the audit/compliance posture already
  flagged as a concern above (provenance/attestation, worker identity).

## 5. Phased roadmap

Each phase below is intended to be independently mergeable and testable without the phases after it being
"real" yet — Phase 0 has no caller, Phase 1 has no injector, Phase 2 has no consumer — matching this
project's preference for landing independent work ahead of coupled work.

Two repo-standing conventions apply across every phase and aren't repeated per-phase below: new REST
surface introduced in gate/orca (Phases 0, 1, 5) should follow this project's established convention of
vendoring an OpenAPI spec rather than hand-writing DTOs/clients; and because every phase here touches at
least one of `kork`/`gate`/`orca`/`clouddriver`/`echo`/`rosco`, `.github/dependencies.yml` is the
authoritative source for which downstream services' tests/builds a given phase's PRs need to validate.

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
- **Phase 7 — Generalized pluggable remote operation execution** (e.g. alternative EC2 deploy logic,
  §3.4). Depends on Phases 0-3 validating the core credential/callback mechanism via baking first.
  Requires its own clouddriver-side design pass: a `Task`-scoped variant of the capability token, a
  decision between the per-operation `AtomicOperation` shape and a formal `OperationExecutor` SPI, and —
  per §4 — an explicit decision to launch with Option A only. Treat as its own follow-up document once
  Phases 0-3 are validated in production, not something to design further here.

## 6. Open questions / follow-ups

- The per-cloud pod-IAM design needed for Phase 4 is explicitly out of scope here and needs its own doc.
- Whether single-use semantics are needed beyond the POST-output claim.
- Whether multi-container `RunJobStage` pods are on any near-term roadmap — affects how much to design
  for token-to-single-container scoping now versus later.
- For Option B specifically: the worker registration/offboarding UX, whether MQTT or `kork-pubsub`'s
  Redis/future SNS-SQS backends should be the reference transport, and how worker-identity credentials
  get issued in the first place. Flag for a dedicated follow-up once Phases 0-3 validate the core model.
- Whether Temporal (or an equivalent durable-workflow engine) is worth adopting specifically as Option B's
  transport layer if/when Option B is built, versus continuing with the `kork-pubsub` announce/claim
  design sketched in §3.2 (see §2.7).
- For §3.4: whether the per-operation `AtomicOperation` shape or a formal `OperationExecutor` SPI is the
  right long-term abstraction, and how `Task.getOwnerId()`/graceful-shutdown semantics should change once
  an operation's execution can outlive the clouddriver pod that dispatched it.
- Whether/how CDEvents (§3.5) should factor into Option B's announcement format, and whether echo's
  existing event model is a good fit for emitting CDEvents-formatted notifications independent of that
  question.
- Whether SPIFFE/SPIRE (§2.9, §3.6) merits a dedicated follow-up design pass, and if so whether scoped
  narrowly (Option A authentication strengthening only) or to the fuller unified Option A/B identity idea.

## 7. Explicitly out of scope / future enhancements

Two further directions came up while scoping this work. Both are genuinely interesting and both are
**explicitly out of scope for this document and its roadmap** — noted here so they aren't lost, and so
nobody mistakes their absence from §5 for an oversight.

**A WebSocket/STOMP transport for Option B.** STOMP (Simple/Streaming Text Orientated Messaging Protocol)
over WebSocket is a lightweight, widely-supported pub/sub transport (used e.g. by Spring's own WebSocket
messaging support, and available as a plugin on brokers like RabbitMQ) that could be a candidate broker
transport for Option B's job announcements, alongside MQTT and `kork-pubsub`'s Redis/SNS-SQS/Kafka
backends discussed in §2.7 and §3.2. It's flagged here as worth evaluating *later*, alongside those other
backends, once Option B's core design (claim-then-exchange, worker identity/registration) is validated —
not designed further in this pass.

**A hybrid Option A/B: temporarily authenticated, listening compute.** Rather than Option A's "one pod,
one job" model or Option B's fully standing worker pool, a middle shape is possible: orca provisions
ephemeral compute the way Option A does (known provenance, orca-controlled lifecycle), but instead of
handling exactly one operation and tearing down immediately, that compute authenticates for a *bounded
session* and listens/subscribes — Option B-style — for a stream of related, CI-type operation requests
(e.g. a burst of build/test/bake requests within one pipeline or time window) before tearing down. This
would blend Option A's provenance guarantees with Option B's efficiency for bursty, related workloads, but
it needs its own credential model (a time-bounded "session" capability broader than a single operation
but still revocable, distinct from both §3.1's single-operation token and Option B's long-lived worker
identity) and its own security analysis (a larger temporal blast radius than a single-operation token, but
smaller than a fully long-lived Option B worker). This is **explicitly out of scope here** — flagged as a
future enhancement worth a dedicated design pass once Phases 0-3 and, ideally, an initial Option B
implementation exist to compare it against.
