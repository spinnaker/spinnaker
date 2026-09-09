# Spinnaker Code Style Guide (for Agents)

This guide captures the code-style expectations of the Spinnaker maintainers, distilled
from real review feedback and merged-PR conventions over recent months (the AWS SDK v1→v2
migration and the Angular-removal follow-ups reviewed/authored by @jasonmcintosh and
@christosarvanitis, plus external contributions).

It has two audiences:

- **Contributor agents** — write code that already matches these conventions, so it passes
  review the first time.
- **Reviewer agents** — check an incoming PR against these rules so maintainers don't have
  to catch every one by hand. For each rule below there's a short "Review check" describing
  what to flag.

Formatting mechanics (indentation, import ordering, line length) are owned by Spotless
(backend) and Prettier/ESLint (frontend) — run them; they are not repeated here. This guide
covers what tooling does **not** enforce.

---

## 1. Reuse existing shared infrastructure — do not reinvent it

The single most consistent piece of maintainer feedback: before writing new "plumbing"
(serializers, client factories, credential handling, Jackson modules, base agents), search
the codebase for an existing shared implementation and use it.

Concrete example from review: a hand-rolled Jackson module for AWS SDK v2 model
(de)serialization was rejected because `com.netflix.spinnaker.clouddriver.aws.jackson.AwsSdkV2Module`
already existed, was registered globally as a Spring bean, and a custom duplicate would
silently shadow the tested one.

Rules:
- Prefer a Spring-managed / already-registered component over constructing your own.
- If you need an `ObjectMapper`, use the injected one (it already has the shared modules);
  only build a standalone mapper when Spring isn't available, and register the **same**
  shared module rather than a new equivalent.
- Consider whether generally-useful code belongs in `kork` (shared libraries) instead of a
  single service. Move it there only when it is a stable cross-service abstraction or has
  multiple concrete consumers; don't increase shared blast radius for hypothetical reuse.

**Review check:** Flag any new serializer, deserializer, Jackson module, client factory,
or credential provider. Ask: does an equivalent already exist in the module or in `kork`?
Flag any newly-constructed `ObjectMapper` where an injected one was available. Before
suggesting a move to `kork`, identify the concrete consumers or shared boundary; possible
future reuse alone is not sufficient.

---

## 2. Prefer builders (Lombok `@Builder`) over hand-rolled `withX()` / setters

For new or rewritten model POJOs, use Lombok:

```java
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EcsMetricAlarm {
  @Builder.Default private List<String> okActions = new ArrayList<>();
  // ...
}
```

- Use `@Builder.Default` for collection fields that must initialize empty.
- Keep backward-compatible accessors (e.g. `getOKActions`/`setOKActions`) when existing
  cache or serialization code still calls them.
- Enable Lombok annotation processing in your IDE.

**Review check:** Flag new POJOs that hand-roll `withX()` chains or a manual builder when
`@Builder` would do. Flag mutable collection fields without `@Builder.Default`.

---

## 3. Import classes; don't use fully-qualified names inline

Write:

```java
import software.amazon.awssdk.services.ecs.model.ListServicesRequest;
...
when(mockEcs.listServices(any(ListServicesRequest.class)))
```

not `any(software.amazon.awssdk.services.ecs.model.ListServicesRequest.class)`.

Only use a fully-qualified name when two required, non-deprecated types have a genuine
simple-name collision in the same file (for example, `java.util.Timer` and
`io.micrometer.core.instrument.Timer`). Import the type used more often and qualify the
other; where practical, structure the code so the collision doesn't arise.

**Review check:** Flag fully-qualified class references in method bodies, arguments, or
mock setups where a plain import would work and there's no name collision.

---

## 4. Name things so the next reader isn't confused

- Avoid stacked/redundant version suffixes. When an API name already carries a version
  (ELBv2), don't add another: `getElasticLoadBalancingV2Client`, **not**
  `getAmazonElasticLoadBalancingV2V2`. Make it clear which is "the API version" and which
  is "the SDK version."
- A name should read clearly to someone who lacks the context you have right now.

**Review check:** Flag names with doubled version tokens (`...V2V2`) or names whose meaning
depends on migration context the reader won't have.

---

## 5. Verify constants and defaults against the source — don't guess

A concrete miss caught in review: an IAM default region was set to `us-east-1`, but AWS SDK
v1's `Regions.DEFAULT_REGION` is actually `us-west-2`. This class of bug frequently comes
from AI-suggested values that were never checked.

- When porting behavior, confirm defaults/constants against the original code, not memory.
- Treat AI-generated values as unverified until checked.

**Review check:** Flag "magic" default values (regions, timeouts, limits, retry counts)
introduced or changed during a port. Ask whether they match the source they claim to
replicate, and request a source reference.

---

## 6. Preserve behavior and extension points during ports/refactors

Maintainers explicitly trace call paths to confirm a rewrite is a faithful translation.
Help them by keeping behavior identical unless the change intends otherwise.

- Don't silently drop customization surfaces. Example: v1 `RequestHandler2` hooks (which
  plugins may use) must have a v2 equivalent (`ExecutionInterceptor`) wired through, even
  if there's no in-tree caller today.
- Call out in the PR description anything that is *intentionally* a behavioral change.

**Review check:** Flag removed hooks/interceptors/handlers, dropped configuration options,
or changed observable behavior that the PR description doesn't explicitly acknowledge.

---

## 7. Guard against pathological runtime behavior

- Traversals over unbounded or cyclic trees/graphs need an explicit termination strategy
  appropriate to the data: a depth bound where truncation is acceptable, cycle detection
  with a visited set, or a documented and enforced upstream bound. If a depth cap can
  truncate valid input, make that behavior explicit and test the boundary.
- Avoid data-access patterns that scale badly. Concrete example: `providerCache.getAll()`
  fetches every entry across **all** accounts and then post-filters to one account/region.
  Use identifier-scoped lookups (`filterIdentifiers()` / identifier-based reads) instead.

**Review check:** Flag recursive or iterative traversal of unbounded/cyclic structures with
no explicit termination strategy. For a fixed depth cap, check that truncation is intentional
and boundary behavior is tested. Flag `getAll()`-style whole-namespace scans that are then
filtered down; suggest identifier-scoped access.

---

## 8. Handle forward-compatible / mutable-vs-immutable model differences

- Newer SDK model objects are immutable (build with `.builder()...build()`); don't expect
  setters.
- **Collections returned by newer SDK getters are unmodifiable.** SDK v1 getters like
  `AutoScalingGroup.getLoadBalancerNames()` returned mutable `ArrayList`s; the v2 equivalents
  (`.loadBalancerNames()`, `.terminationPolicies()`, `.securityGroups()`, …) return
  **unmodifiable** lists. Assigning one into a field you later mutate throws
  `UnsupportedOperationException` at runtime (not compile time). Copy into a new mutable
  collection (`new ArrayList<>(response.loadBalancerNames())`) before mutating.
- Newer SDK enums return an `UNKNOWN_TO_SDK_VERSION` sentinel instead of throwing on
  unrecognized values. Validation that relied on an exception from `fromValue()` must be
  rewritten to handle the sentinel.
- **Honor non-null contracts.** Fields documented non-null (e.g. `@Empty`-documented
  `Application.clusterNames`) must be initialized, or downstream code (`ClusterController.mergeClusters`)
  hits a `NullPointerException`. Initialize collection/map fields rather than leaving them null.

**Review check:** Flag validation logic that assumes an enum parse throws. Flag mutation of
objects that are immutable in the target model. Flag assigning an SDK-returned collection
into a field that is later mutated (`add`/`remove`/`clear`) without a defensive copy. Flag
uninitialized fields that an interface documents as non-null/non-empty.

---

## 9. License headers on new files

Every new source file gets the standard license/copyright header, matching the format of
neighboring files. For community contributions the entity is typically `spinnaker.io` (or
the contributing organization).

**Review check:** Flag new source files missing a header, or with a copyright entity
inconsistent with the rest of the repo.

---

## 10. Leave a trail for deferred work

When you knowingly defer something, leave a `TODO` with enough context to act on later, and
mention it in the PR description with a link to the tracking issue when one exists. If no
tracking issue exists, say so and ask the maintainer whether to create one; never invent an
issue number or link. Don't let known gaps disappear silently.

**Review check:** For anything the PR says is "deferred" or "follow-up," check there's an
actionable `TODO` and, when available, a valid issue reference. Call out a missing tracking
issue rather than fabricating one.

---

## 11. Match the pattern already used by sibling code

When fixing or adding something that has parallels elsewhere (other cloud providers, other
command builders, other stages), follow the shape the existing siblings already use rather
than inventing a local variation.

Concrete example: an ECS server-group command builder crashed on `null` defaults because it
used an ES6 default parameter (`defaults = {}`), which only kicks in for `undefined`. The fix
adopted the `defaults = defaults || {}` normalization the AWS and Google builders already
used — consistency, not a new approach.

Explicit rules in this guide and the surviving architecture take precedence over sibling
consistency. Compare against current, non-deprecated siblings; don't reproduce a legacy
pattern merely because it is common nearby.

**Review check:** When a change touches one of N parallel implementations, check whether the
other N-1 already established a current, non-deprecated pattern the change should follow.
Flag divergence that isn't justified, but don't request consistency with a pattern this guide
marks as removed or deprecated.

---

## 12. Fix the bug class, not just the instance; add a regression test

The maintainers routinely fix a bug and then audit sibling code for the *same class* of bug,
and they expect a test that reproduces the original failure.

- Add a regression test that fails before the fix and passes after (e.g. "builds a valid
  command when `null` defaults are passed").
- After fixing an instance, audit the obvious siblings for the same mistake and say what you
  found (even "audited all other providers; ECS was the only offender" is valuable).
- Keep the resulting patch scope explicit. Fix additional instances only when they share the
  same root cause and can be addressed safely in the current change; otherwise report them
  and track follow-up work separately. Don't silently bundle adjacent refactors.

**Review check:** Flag bug-fix PRs with no regression test. Ask whether sibling code shares
the same defect and whether it was checked. Don't require every related match to be folded
into the current PR when doing so would add unrelated scope or risk; request a follow-up
instead.

---

## 13. Tests must exercise runtime behavior, not just shape

A test that only reflects an annotation or asserts on string contents can pass while the real
runtime wiring is broken. This was called out explicitly for a security-annotation test: it
checked the annotation was present but never ran the endpoint through Spring method security,
so a real SpEL/security-wiring bug would have slipped through.

- Prefer tests that invoke the real path (through Spring security, through the actual
  serializer, through the cache round-trip) over tests that inspect metadata.
- For security-sensitive changes, prove the *unauthorized* case is actually rejected.

**Review check:** Flag tests that only assert on annotations, class shape, or string
contents where a behavioral test is feasible — especially for auth/security wiring.

---

## 14. Prefer Java over Groovy/Spock for new backend tests

This is a soft preference, not a hard rule. A maintainer has signaled an intent to start a
Groovy/Spock-to-Java test migration "soonish" and would rather new tests land in Java, while
explicitly being "ok for now" with Groovy — and Groovy specs were still being added during
the 2026.3.0 cycle. So when writing a new backend test from scratch, Java (JUnit 5) is the
forward-looking choice, but Groovy/Spock is still accepted, especially when it matches the
surrounding test suite.

**Review check:** For a brand-new Groovy/Spock spec, it's reasonable to gently note that Java
is preferred going forward — but don't treat it as blocking, and don't push back on Groovy
that's consistent with an existing Groovy suite.

---

## 15. Security: don't widen access or trust redirects

Recent fixes centered on two recurring security mistakes:

- **Accidental auth bypass.** New endpoints/tools (including AI-generated ones) can quietly
  skip the authorization that the normal path enforces — e.g. reaching around front50's
  auth. New read/write surfaces must go through the same authorization as their existing
  equivalents.
- **Unvalidated redirects (SSRF).** HTTP redirect `Location` headers must be validated against
  the configured `HttpUrlRestrictions`; an allowed external host must not be a pivot to
  internal endpoints. `urlRestrictions` must actually be wired (not left null).

**Review check:** For any new endpoint, MCP tool, or artifact fetcher, confirm it enforces the
same authorization and URL restrictions as the established path. Flag redirect-following HTTP
clients that don't re-validate the redirect target. This is exactly the class of issue that
justifies careful review of AI-authored PRs.

---

## 16. Don't write new code against deprecated/removed technology

The 2026.3.0 release removed a large amount of legacy tech and put more on a countdown. New
code must target the surviving path, not the one being retired. Building on a deprecated
component creates work that has to be redone before it's even merged.

Lifecycle status is relative to the PR's target branch. Before applying the lists below in a
review, establish the target branch and compare against an up-to-date target ref. Older
release branches may still contain technology already removed from `main`; unchanged legacy
usage there is not itself a finding. Flag newly introduced or expanded usage unless it is
explicitly required by the scoped backport.

Already **removed** (do not use at all in new code):

- **AWS SDK v1 (`com.amazonaws` / `aws-java-sdk`)** — fully removed; the whole codebase is on
  SDK v2 (`software.amazon.awssdk`). Any v1 import is a mistake.
- **AngularJS** — removed from Deck; all UI is React. No new Angular, no Angular/React bridges.
- **Halyard** — removed from the codebase.
- **Old Kubernetes API types** — `extensions/v1beta1` and `networking.k8s.io/v1beta1` are gone.
- **Custom SAML config** — moved to native Spring Security SAML; don't add to the old
  Spinnaker-specific SAML properties.

Deprecated and **on a removal timeline** — prefer the replacement, don't extend the old one:

| Deprecated | Replacement | Removed in |
|-----------|-------------|-----------|
| Spectator metrics | Micrometer (native Spring metrics) | 2027.0.0 (stackdriver feeds 2026.4.0) |
| Redis storage for Orca **executions** | SQL | 2027.0.0 |
| Non-SQL Front50 storage (S3/GCS/blob) for pipelines/templates | SQL | 2027.0.0 |
| Titus cloud provider | — (being retired) | 2027.0.0 |
| Kustomize 3 | Kustomize 4 or 5 | 2027.0.0 |

Notes:
- **Metrics:** new instrumentation should use the Micrometer `MeterRegistry`, not `Registry`
  (Spectator). This was already flagged in review before it hit the roadmap. If you must touch
  Spectator code, leave a `TODO` pointing at the Micrometer migration (see §10).
- **Titus:** don't build new features on the Titus provider; it's deprecated with no active
  contribution.
- Redis is still the recommended **queue** backend — only *execution/pipeline storage* is
  moving to SQL. Don't conflate the two.
- **Artifact credentials:** `BaseHttpArtifactCredentials.getHeaders(account)` now declares
  `throws IOException`, and there's a URL-aware `getHeaders(account, HttpUrl)` overload for
  auth material that depends on the fetched URL. Override the URL-aware method when auth is
  URL-dependent; keep `throws IOException` on overrides.

**Review check:** Flag any new `com.amazonaws`, AngularJS, Halyard, or removed-K8s-API usage
outright. Flag new Spectator `Registry` instrumentation (suggest Micrometer), new non-SQL
storage code paths, and new feature work on the Titus provider. Flag new Kustomize-3-only
assumptions.

---

## PR description conventions

Merged PRs in this repo consistently use a structured description. Follow it:

- **Summary** of what changed and why (root cause for fixes).
- **Root causes & fixes** for bug fixes — explain the underlying mechanism, not just the symptom.
- **Test plan** with checkboxes and the actual command/result
  (e.g. `- [x] ./gradlew :deck:test — BUILD SUCCESSFUL`).
- **Dependency callouts** when stacked on another PR ("Depends on #7954"; note the diff will
  shrink once it lands).
- **Severity table** for security fixes (component / severity).
- Reference the tracking issue (`Part of spinnaker/spinnaker#NNNN`).

**Review check:** Flag PRs missing a test plan or, for fixes, a root-cause explanation. Flag
stacked PRs that don't state their dependency.

---

## Using this guide as a reviewer agent

When asked to review a PR, walk sections 1–16, but report only concerns introduced or newly
exposed by the diff. Don't report unrelated pre-existing problems unless the change makes
them reachable, more severe, or materially harder to fix.

Treat each **Review check** as an investigation prompt, not an automatic finding. Search for
the existing implementation, trace the affected call path, or establish a concrete failure
mode before reporting a defect. For example, a new serializer triggers the §1 search; its
existence alone does not prove that shared infrastructure was reinvented. If a concern cannot
be verified from the available evidence, present it as a question rather than asserting that
it is a defect.

Assign severity from demonstrated impact, not solely from the rule number:

- **Blocking** — a confirmed security, correctness, data-loss, runtime-failure, or target-branch
  compatibility issue that must be resolved before merge. Typical examples include an auth
  bypass or SSRF (§15), code newly built on technology removed from the target branch (§16),
  a confirmed behavioral/extension-point regression (§6), or a demonstrated runtime crash
  from an immutable or null model mismatch (§8).
- **Should fix** — a verified maintainability, performance, migration, or testing problem that
  should normally be addressed before merge but is not yet a demonstrated correctness or
  security failure. Typical examples include duplicated shared infrastructure (§1), new use
  of deprecated-but-not-yet-removed technology (§16), a missing regression test (§12), or an
  unjustified sibling-pattern divergence (§11).
- **Nit** — a localized, non-behavioral consistency issue such as imports (§3), headers (§9),
  TODO trail (§10), or the Java preference for new tests (§14, never blocking).
- **Question** — a plausible concern that lacks enough evidence to classify as a finding, such
  as a default whose source has not yet been established (§5). State what evidence would
  resolve the question.

For every finding, cite the file and line, the applicable rule, the concrete impact, and the
evidence that establishes it. Keep behavioral concerns separate from stylistic ones. Because
a lot of work here is AI-assisted, apply extra scrutiny to security surfaces (§15) and
unverified constants (§5) — while still following the same evidence standard.
