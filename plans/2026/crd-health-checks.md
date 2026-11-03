# Custom Resource Health Checks (clouddriver-kubernetes)

Status: proposed · Branch: `crdHealthChecks` · Created: 2026-09-28

## Problem

Clouddriver reports every custom resource (CR) as healthy. All three handlers that can serve
a CR return a hard-coded default status (stable, available, not failed):

| Path | Handler | `status()` |
|---|---|---|
| CRDs discovered from the cluster (`KubernetesCredentials` CRD refresh) | `KubernetesCustomResourceHandler` | `Status.defaultStatus()` |
| CRDs declared in the account's `customResources:` config | `CustomKubernetesHandlerFactory.Handler` | `Status.defaultStatus()` |
| CRs whose kind isn't registered | `KubernetesUnregisteredCustomResourceHandler` | `Status.defaultStatus()` |

Status is computed when a manifest is read (`KubernetesManifestContainerBuilder.buildManifest`),
from the live object that `KubernetesManifestProvider` fetches. Orca's
`WaitForManifestStableTask` trusts `stable` and `failed` as returned. As a result, Deploy Manifest
continues immediately for Argo `Rollout`, cert-manager `Certificate`, Crossplane claims, Knative
`Service` and similar kinds, even after their controller reports `Ready=False`. Today the only way
to get real status is a plugin that provides a whole `KubernetesHandler` per kind.

## Goals

- Status that is correct by default for CRs following the upstream Kubernetes conventions.
- Declarative, operator-configurable rules for CRDs that don't follow those conventions, with no
  code execution.
- A per-manifest override for teams without clouddriver config access.
- A supported plugin extension point for kinds that need custom logic.
- No change in behaviour until an operator opts in; the default flips in a later release.

## Non-goals

- Changes to Orca's or Deck's status logic: both already use `stable`, `failed` and `paused`.
  (Two small rendering changes are needed; see "Caching, SpEL and UI impact".)
- Caching changes: status is never cached (see "Caching, SpEL and UI impact").

## Design

### 1. Default evaluator (kstatus semantics)

This follows `sigs.k8s.io/cli-utils/pkg/kstatus`, the convention Flux, kpt and `kubectl wait` use.
The first match wins:

1. No `status` block → **stable** (same as today; covers config-only CRs).
2. `metadata.generation` ≠ `status.observedGeneration` → **unstable**
   (`UnstableReason.OLD_GENERATION`). Only compared when both are present and
   `observedGeneration` is an integer (or a numeric string). Argo Rollouts declares
   `status.observedGeneration` as a string, and a non-numeric value must skip this step rather
   than leave the resource unstable forever.
3. `Stalled=True` condition → **failed** (condition reason and message).
4. `Reconciling=True` condition → **unstable**.
5. `Ready` condition:
   - `True` → **stable**.
   - `False` or `Unknown` → **unstable**. It is **failed** only when a rule lists the reason as
     terminal.
   - A condition whose own `observedGeneration` is older than the object's generation is treated
     as unstable.
6. `status.phase` fallback: `Failed`/`Degraded` → **failed**, `Pending`/`Progressing` →
   **unstable**, `Paused` → **paused**.
7. Otherwise → **stable**.

Implement this as one shared Spring bean, `CustomResourceStatusEvaluator`, and have all three
handlers delegate to it. The evaluator must not live as state on per-kind handler instances:
`GlobalResourcePropertyRegistry.updateCrdProperties` is shared across accounts and each account's
CRD refresh overwrites it. That's harmless now because every CRD handler is identical, but it
would break per-account behaviour.

### 2. Declarative rules

Health semantics belong to the CRD, not the account, so the config is service-wide. It binds as a
nested `customResourceStatus` section of `KubernetesConfigurationProperties`, next to the existing
`jobExecutor`, `cache` and `kubectl` sections:

```yaml
kubernetes:
  customResourceStatus:
    enabled: false                     # PR 5 flips this default
    rules:
      - kind: Rollout.argoproj.io
        stable:  [{ path: "$.status.phase", in: [Healthy] }]
        paused:  [{ path: "$.status.phase", in: [Paused] }]
        failed:  [{ path: "$.status.phase", in: [Degraded] }]
      - kind: Certificate.cert-manager.io
        readyCondition: Ready
        failedReasons: [Failed]
      - kind: Widget.example.com
        ignoreStatus: true
```

- Matchers use JSONPath for reads only, with the operators `equals`, `in`, `notIn` and `exists`.
  They have no expression language (see "Security considerations").
- By default a rule adds to the evaluator in section 1. `replaceDefault: true` makes the rule
  replace it for that kind.
- The bound section is plain mutable configuration. A `@Bean` method compiles it once, at startup,
  into an immutable `CustomResourceStatusRules` (fail fast on a bad path or kind), and the
  evaluator only ever sees the compiled form. Don't compile lazily or only inside a builder:
  config bound from YAML goes through the no-arg constructor and setters, so builder-only
  initialisation never runs.
- Status messages come from the matched condition's `message` when there is one; otherwise they
  are a fixed string that names the rule.

### 3. Per-manifest annotations

Supported annotations: `status.spinnaker.io/ready-condition`, `status.spinnaker.io/failed-reasons`
and `status.spinnaker.io/ignore`.

- Each value is a condition name, a comma-separated list of reasons, or a boolean. None is an
  expression or a path.
- Precedence: annotation > rule > default.

### 4. Plugin extension point

Add `CustomResourceStatusProvider` (`supports(KubernetesKind)` and `status(KubernetesManifest)`)
as a `SpinnakerExtensionPoint`. Providers are checked before rules.

## Security considerations

### Why rules don't use SpEL

SpEL was considered for rules and rejected. Clouddriver holds credentials for every configured
cloud account, so any code execution there has the largest blast radius in Spinnaker.

| Rule source | Who controls it | SpEL acceptable? |
|---|---|---|
| Clouddriver config (`customResourceStatus.rules`) | Spinnaker operators | Only with a restricted context (see below). Not needed for v1. |
| Manifest annotations | Anyone who can deploy the manifest, *or edit the object out-of-band with kubectl* | **Never** |
| CR `status` contents | The CR's controller and anyone with `update` on the `status` subresource (or on the object, if the CRD has no status subresource) | **Never** as expression input |

If SpEL is ever added for config-sourced rules, it must meet all of the following:

- Use `SimpleEvaluationContext.forReadOnlyDataBinding()`, never `StandardEvaluationContext`.
  `StandardEvaluationContext` allows `T(...)` type references, constructors and bean references,
  which amounts to remote code execution.
- Parse expressions once at startup and cache them. Never build an expression string by
  concatenating manifest data.
- Enforce a maximum expression length and an evaluation time budget. Status is evaluated on every
  manifest read, including each `WaitForManifestStable` poll and each UI view.
- Don't reuse Orca's `ExpressionsSupport` function registry (`#fromUrl` etc.) in clouddriver.

Annotations are never expressions for a second reason. Orca already SpEL-evaluates manifest
bodies at deploy time unless `skipExpressionEvaluation` is set, so `${...}` in an annotation would
be evaluated by Orca, not clouddriver, which is confusing and a separate trust boundary.

### Status text is untrusted data

Free-form controller text (condition `message`/`reason`) is attacker-influenced whenever anyone
other than Spinnaker can write the object's status. It already reaches Orca stage context today
through deploy results (see "Caching, SpEL and UI impact"). This change adds it to
`WaitForManifestStableTask`'s messages as well. Requirements:

- Downstream code must treat status and condition text as data only. It must never become SpEL
  template input or be interpreted as markup.
- Clouddriver truncates each message (configurable, default 1 KiB) and strips control
  characters.
- **Blocking dependency for PR 5 (flip the default):** Orca-side handling of this text needs a
  separate review before the default changes. This is tracked separately.

### Dependencies

`clouddriver-kubernetes.gradle` pins `com.jayway.jsonpath:json-path:2.3.0`, while the rest of the
monorepo uses the managed version. Drop the pin in PR 2, and check the resolved version against
known json-path CVEs before rule paths are accepted from config.

## Caching, SpEL and UI impact

### Caching

- **Status is never cached.** `KubernetesHandler.status()` has one caller,
  `KubernetesManifestContainerBuilder`, used only by `KubernetesManifestProvider.getManifest`.
  That reads the object live (`credentials.get`, i.e. `kubectl get`). `getClusterAndSortAscending`
  and `getClusterManifestCoordinates` are also live (`credentials.list`) and don't compute status.
- **Cache-backed views are unaffected.** Clusters and server groups take health from cached
  pods, and the Resources tab (`KubernetesRawResource`) has no status field. Neither calls
  `status()`. There are no caching-agent, cache-format or migration changes.
- **Cost.** One evaluation per live manifest read: each `WaitForManifestStable` poll and each Deck
  status poll. That's dominated by the `kubectl get`; rule paths are compiled once at startup.
- **Wiring.** The evaluator must reach every place a CR handler is built:
  - the CRD refresh in `KubernetesCredentials` (`new KubernetesCustomResourceHandler(kind)`);
  - the static `CustomKubernetesHandlerFactory.create`;
  - the `KubernetesUnregisteredCustomResourceHandler` bean.
- **Follow-up, out of scope.** Showing CR status in the Resources tab would mean evaluating against
  cached manifests, which are stale by up to the caching interval. That's acceptable for display
  only; `WaitForManifestStable` must keep using live reads.

### SpEL

- **Deploy results already carry status.** Deploys run `kubectl apply -o json`, so the returned
  manifest includes the object's current `status` block. `PromoteManifestKatoOutputsTask` stores
  those manifests in stage context (`outputs.manifests`, alongside `kato.tasks`). For a
  redeployed CR, controller text is therefore already in stage context, independent of this plan.
- **This plan adds one more path:** condition messages in `WaitForManifestStableTask`'s `messages`
  and failure details.
- **One review covers both.** The Orca-side review that blocks PR 5 covers both paths.
- **Clouddriver-side limits still apply.** Truncation and control-character stripping (see
  "Security considerations") apply to messages the evaluator emits.

### UI

- **What starts changing:**
  - **Deploy Manifest execution details** (`DeployStatus`, `DeployStatusPills`, `ManifestStatus`)
    poll the live manifest, so they show real stable/failed/paused state for CRs instead of
    always-stable.
  - **Server group and server group manager detail panels** also show "Transitioning" / "Rollout
    Paused" bands, for CRs mapped to those Spinnaker kinds through `customResources`.
- **What doesn't change:** the Resources tab and cluster views.
- **Rendering.** Deck renders these messages through its `Markdown` component: the `Tooltip` in
  `ManifestStatus`, and `StageFailureMessage` for stage failures. Output is DOMPurify-sanitised,
  but links and images still render, and controller-written text should appear as plain text.
  Two small changes (PR 0):
  - Deck: `ManifestStatus` tooltips render status messages as plain text.
  - Orca: `WaitForManifestStableTask` formats cluster-sourced text in failure messages as an
    inline code span, so Markdown renders it literally.
- **Annotations don't collide.** The `status.spinnaker.io/*` annotations (section 3) don't
  overlap the `*.details.spinnaker.io` keys Deck's `AnnotationCustomSections` renders.

## Decisions

- **Built-in rules for popular CRDs: none in v1.** The default evaluator already covers the
  common cases:
  - cert-manager, Crossplane, Flux and Knative report `Ready`/`Stalled` conditions;
  - Argo Rollouts is covered by the `status.phase` fallback and the string `observedGeneration`
    handling in step 2.
  Built-in rules would be code tracking third-party CRD versions. Instead, document example
  rules, and keep each documented example as a test fixture so the docs can't drift from the
  behaviour. Revisit if users report kinds the default gets wrong.
- **`Ready=Unknown`: no grace period.** It stays **unstable**, matching kstatus. Treating it as
  stable after a delay would bring back the false "healthy" this plan exists to remove. A resource
  stuck there fails through the stage's existing timeout (`stableManifestTimeoutMinutes`) with the
  condition message. Operators can opt a kind or object out with `ignoreStatus` or
  `status.spinnaker.io/ignore`. A CRD installed without a controller has no `status` block, so
  step 1 already treats it as stable.

## PR sequence

Independent PRs land first.

0. **Deck + Orca: render controller text as plain text.** This covers the two rendering changes
   under "UI". It is useful on its own and independent of the rest. Tests: Deck spec for the
   tooltip text; Orca test that a failure message containing Markdown renders literally.
1. **clouddriver: default evaluator.** Covers the `CustomResourceStatusEvaluator` bean, delegation
   from all three handlers, the `kubernetes.customResourceStatus.enabled` flag (default `false`)
   and message truncation/sanitisation.
   Tests: a JUnit 5 table test for each step of section 1, plus fixture CRs (Argo Rollout,
   cert-manager Certificate, Crossplane claim, a CR with no status). Also a test that CRD handlers
   discovered from the cluster and CRD handlers from config both delegate.
2. **clouddriver: declarative rules.** Covers config binding, startup validation, JSONPath
   matchers and removing the json-path pin. Tests:
   - rule precedence, `replaceDefault`, and an invalid path failing startup;
   - an `ApplicationContextRunner` test that binds rules from properties (both `customResourceStatus`
     and `custom-resource-status` key styles) and then evaluates a manifest with the resulting bean.
     Rules built only through a builder in tests wouldn't catch config that never gets compiled.
3. **clouddriver: annotations.** Tests: annotation > rule > default.
4. **clouddriver: plugin extension point.** Tests: a provider wins over rules.
5. **Flip the default to `enabled: true`.** Add a release note: pipelines that deploy CRs will
   now wait, and can fail on `Stalled` or terminal reasons. Blocked on the Orca-side review under
   "Security considerations".
