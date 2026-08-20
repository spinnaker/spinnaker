# gate-mcp

A Model Context Protocol (MCP) server for Spinnaker, embedded in Gate. It exposes a curated set
of tools, resources, and prompts backed by the same gate-core Retrofit clients (Orca, Clouddriver,
Front50, Kayenta, Keel) that power Gate's own REST controllers, so MCP requests inherit the same
authentication (SAML/OAuth2/x509/session) that Gate already enforces for every other endpoint.

Built on [Spring AI's MCP server support](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html)
(`spring-ai-starter-mcp-server-webmvc`), using the `@McpTool`/`@McpResource`/`@McpPrompt`/`@McpArg`
annotations from `org.springaicommunity.mcp.annotation` (transitively pulled in by that starter)
for scanning.

## Enabling it

Disabled by default. In `gate.yml` (or `gate-local.yml`):

```yaml
mcp:
  server:
    enabled: true # turns on the MCP endpoint
    read-only: false # allow mutating tools to actually execute (defaults to true)
```

`read-only: true` (the default once `enabled: true`) makes every mutating tool reject with an
error instead of executing - a safe way to expose read/query tools to an MCP client before opting
into write access.

## Tool catalog

### Applications & infrastructure

| Tool | Description |
|---|---|
| `list_applications` | List applications, filterable by owner email |
| `get_application` | Get a single application's front50 metadata |
| `create_application` | Create/update an application |
| `delete_application` | Delete an application's front50 metadata |
| `get_clusters` | List an application's clusters by account |
| `get_server_groups` | List an application's server groups |
| `get_load_balancers` | List load balancers for a cloud provider |
| `submit_orchestration` | Generic escape hatch: submit a raw Orca job list (any clouddriver operation), blocking until it completes |
| `deploy_aws_server_group` | Typed convenience wrapper: deploy a new AWS server group from an AMI id |
| `upsert_load_balancer` | Semi-typed wrapper: create/update a load balancer |

### Global search (`SearchTools`)

Proxies clouddriver's `/search` endpoint (its cached Cats index, not a live infrastructure call).
Fixed as part of this module (see the design note below) to actually support searching multiple
types in one call, which Gate's REST API had never been able to do end-to-end.

| Tool | Description |
|---|---|
| `search_infrastructure` | Search one or more types in a single, relevance-ranked call (`applications`, `clusters`, `serverGroups`, `instances`, `loadBalancers`, `securityGroups`, `projects`, plus provider-specific types) |
| `search_all_types` | Search across all (or a chosen subset of) types at once, giving each type its own independent result budget by fanning out one call per type and merging results - the MCP equivalent of Deck's global search bar |

### Pipeline definitions (`PipelineConfigTools`)

| Tool | Description |
|---|---|
| `list_pipeline_configs` | List an application's pipeline definitions |
| `get_pipeline_config` | Retrieve a single pipeline definition by name or id |
| `save_pipeline_config` | Create or update a pipeline definition (upsert by `id`) |
| `delete_pipeline_config` | Delete a pipeline definition by name |

### Pipeline executions (`PipelineTools`)

| Tool | Description |
|---|---|
| `trigger_pipeline` | Start a pipeline execution |
| `get_pipeline_execution` | Get a pipeline execution by id |
| `cancel_pipeline` / `pause_pipeline` / `resume_pipeline` | Control a running execution |

### Execution search & triage (`ExecutionTools`)

| Tool | Description |
|---|---|
| `search_executions` | Search executions by trigger criteria (type, event id, time range, status) |
| `get_latest_executions` | Fetch latest execution(s) by pipeline config id, or specific executions by id |
| `get_failed_stages` | Retrieve failed stages for an execution, descending into nested pipelines |

### Manual judgments (`ManualJudgmentTools`)

| Tool | Description |
|---|---|
| `list_pending_manual_judgments` | Find executions paused on a manual judgment stage, for an application |
| `get_manual_judgment` | Get the pending manual judgment stage(s) for one execution |
| `judge_pipeline_stage` | Continue/stop a manual judgment stage |

### Tasks (`TaskTools`)

| Tool | Description |
|---|---|
| `search_application_tasks` | Search/list an application's ad-hoc tasks, paginated and status-filterable |
| `get_task` | Retrieve a task by id |
| `create_task` | Submit a raw ad-hoc task and return immediately (fire-and-forget; poll with `get_task`) |
| `cancel_task` | Cancel a running task |

### Canary analysis (`KayentaTools`, only when `services.kayenta.enabled: true`)

| Tool | Description |
|---|---|
| `list_canary_configs` / `get_canary_config` | Read canary configs |
| `save_canary_config` | Create or update a canary config (upsert by `id`) |
| `delete_canary_config` | Delete a canary config |
| `list_canary_judges` | List configured canary judges |
| `list_canary_metrics_metadata` | List metric descriptors available from a metrics source |
| `test_canary_metric_query` | Run a single metric query against a live metrics source without saving anything - the same "Test Query" feature in Deck's canary config editor |
| `initiate_canary` / `initiate_canary_with_config` | Start a canary judgement run (saved config, or an inline/ad-hoc config) |
| `get_canary_result` / `list_canary_results_by_application` | Read canary judgement results |
| `get_canary_metric_set_pair_list` | Read the per-metric experiment/control data behind a judgement |

### Managed Delivery / Keel (`KeelTools`, only when `services.keel.enabled: true`)

The highest-value CD surface here short of pipeline executions themselves - Spinnaker's
GitOps-style declarative delivery: what's deployed where, what's blocking promotion, and the
controls used when a rollout is stuck.

| Tool | Description |
|---|---|
| `get_delivery_config` / `get_delivery_config_for_application` | Read a delivery config manifest by name, or the one associated with an application |
| `get_delivery_config_artifacts` | Status of each artifact version tracked by a manifest, across environments |
| `get_delivery_config_schema` | JSON schema for a manifest - use to construct a valid one |
| `validate_delivery_config` / `diff_delivery_config` | Validate or diff a manifest without saving it |
| `save_delivery_config` / `delete_delivery_config` | Create/update or delete a manifest |
| `get_managed_application` | Environments, resources, and their status for an application |
| `get_managed_environments` | Current environments (and artifact/constraint state) for an application |
| `get_environment_constraints` | Recent constraint states (e.g. manual judgment, canary) for an environment |
| `update_environment_constraint_status` | Approve/reject a pending constraint blocking promotion |
| `get_managed_resource` / `get_managed_resource_status` | Read a single resource / its status |
| `pause_managed_application` / `resume_managed_application` | Pause/resume all automated management for an application |
| `pause_managed_resource` / `resume_managed_resource` | Pause/resume automated management for one resource |
| `pin_artifact_version` / `unpin_artifact_version` | Pin/unpin an artifact version in an environment |
| `veto_artifact_version` / `remove_artifact_veto` | Block/unblock an artifact version from an environment |
| `mark_artifact_version_bad` / `mark_artifact_version_good` | Permanently mark a version bad, or clear that mark |
| `override_verification` / `retry_verification` | Force a post-deploy verification's status, or retry it |

## Resources

| URI | Description |
|---|---|
| `spinnaker://applications/{application}` | Application detail |
| `spinnaker://applications/{application}/pipelines` | Recent executions |
| `spinnaker://executions/{executionId}` | Execution detail |
| `spinnaker://applications/{application}/manual-judgments` | Pending manual judgments |

## Prompts

| Name | Description |
|---|---|
| `triage-failed-pipeline` | Investigate a failed execution and recommend next action |
| `review-manual-judgment` | Gather context before approving/rejecting a manual judgment |

## Design notes

- **Module boundary**: gate-mcp is a dependency *of* gate-web (so its beans load into the running
  Gate process), which means it **cannot** depend on gate-web in turn - so it can't reuse
  gate-web's controllers/`*Service` classes directly. Every tool instead talks to the lower-level
  gate-core Retrofit client interfaces (`OrcaServiceSelector`, `ClouddriverServiceSelector`,
  `Front50Service`, `KayentaService`, `KeelService`, `TaskService`) that those gate-web classes
  themselves wrap. `KayentaService` was moved from gate-web to gate-core to make this possible
  (alongside `OrcaService`/`ClouddriverService`/`Front50Service`/`EchoService`/`KeelService`, which
  already lived there) without duplicating a second Retrofit client. Gate's *authentication* is
  still fully inherited (enforced by a servlet filter chain in front of every endpoint, including
  the MCP transport), but *authorization* is a different story: bypassing gate-web's controllers
  also bypasses whatever `@PreAuthorize`/`@PostFilter` Fiat checks live on those controllers
  specifically, as opposed to on the downstream service each controller calls. Every tool in this
  module was individually audited against its downstream service's own authorization behavior:
  - **front50, orca, clouddriver, and keel-web all self-enforce Fiat checks** on the endpoints
    these tools call, independent of which client calls them (identity propagates from Gate's
    request filter chain through to these services via the standard `X-SPINNAKER-USER`/allowed-
    accounts headers, so a downstream `@PreAuthorize`/`@PostFilter` evaluates against the real
    calling user). This covers `get_application`, `create_application`/`delete_application`,
    `search_infrastructure`/`search_all_types`, `submit_orchestration` and every typed deploy/LB
    tool, `get_clusters`/`get_server_groups`/`get_load_balancers`, all of `PipelineTools`/
    `ExecutionTools`/`ManualJudgmentTools`/`TaskTools` (backed by orca's `TaskController`),
    `PipelineConfigTools` (backed by front50's `PipelineController`), all of `KeelTools` (keel-web
    checks permissions explicitly via `AuthorizationSupport` on every controller), and
    `SpinnakerResources`.
  - **`list_applications` was a genuine, gate-mcp-specific bypass and has been fixed.** It called
    front50's `?restricted=false` endpoint (`Front50Service.getAllApplicationsUnrestricted`) -
    the same escape hatch gate-web's own `ApplicationController.getAllApplications` uses, but
    gate-web immediately re-applies the missing check with its own
    `@PostFilter("hasPermission(filterObject.name, 'APPLICATION', 'READ')")`. Since gate-mcp can't
    reuse that controller, the method now carries the equivalent
    `@PostFilter("hasPermission(filterObject.get('name'), 'APPLICATION', 'READ')")` directly
    (Spring method security is enabled globally in gate-core via
    `SpringSecurityAnnotationConfig`, and applies to any Spring-managed bean, including the plain
    `@Bean`-registered tool classes here) - see `ApplicationToolsAuthorizationTest`, which proves
    the filter is live through a real Spring AOP proxy, not just present in source.
  - **`get_task` has the same exposure as Deck already has, not a new one.** Orca's
    `GET /tasks/{id}` deliberately ships with its `@PostAuthorize` commented out (see the comment
    in `TaskController.groovy`: Deck polls this endpoint immediately after application creation,
    before Fiat permissions have propagated, and task ids are hard-to-guess GUIDs). gate-web's own
    `TaskController` has no additional check either, so this tool's exposure is identical to
    Deck's - not worsened by going through gate-mcp.
  - **All of `KayentaTools` inherits a pre-existing, platform-wide gap, not one specific to
    gate-mcp.** Kayenta has no Fiat integration of its own (`WebSecurityConfig.securityFilterChain`
    is `permitAll()` for every request, with a `TODO: If we choose to use fiat, this needs to be
    removed`), and gate-web's own `CanaryController` proxy has no `@PreAuthorize` either - so any
    authenticated Spinnaker user can already read/write any application's canary configs through
    Deck's canary UI today. `KayentaTools` has that same exposure, no more and no less. Fixing this
    properly means adding real Fiat enforcement to Kayenta/gate-web's canary proxy, which is out of
    scope for gate-mcp to bolt on unilaterally (it would make the MCP surface *more* restrictive
    than Deck for the same operations, not fix the actual gap) - tracked as a platform-level
    follow-up, not a gate-mcp bug.
- **`submit_orchestration`** / **`create_task`** are the generic primitives every other mutating
  tool builds on - the `{application, description, job: [...]}` payload Deck submits for every
  write operation (see `OrchestrationJobs` / `TaskService` in gate-core). Any clouddriver-supported
  operation for any cloud provider can be submitted through them even without a dedicated typed
  tool. `submit_orchestration` blocks until the task finishes; `create_task` returns immediately.
- Tool/resource/prompt classes are plain POJOs registered via `@Bean` methods in
  `McpServerAutoConfiguration` (not `@Component`-scanned), so when `mcp.server.enabled` is false
  none of them exist in the application context at all. `KayentaTools`/`KeelTools` additionally
  require `services.kayenta.enabled: true`/`services.keel.enabled: true` via their own
  `@ConditionalOnProperty` - deliberately *not* `@ConditionalOnBean(KayentaService.class)`/
  `@ConditionalOnBean(KeelService.class)`, since condition evaluation order across two
  independently classpath-scanned `@Configuration` classes (this module's and gate-web's
  `GateConfig`) isn't guaranteed, while a property condition is deterministic.
- `KeelTools`' small request bodies (pin/veto/constraint-status/verification requests) are exposed
  as individual scalar tool parameters rather than opaque maps, for a cleaner tool-call JSON
  schema; only the full delivery config manifest (too large/nested to usefully flatten) is a
  free-form map, converted to `DeliveryConfig` with Jackson (`ObjectMapper.convertValue`) before
  calling `KeelService` - the only tool class in this module that needs an `ObjectMapper`.
- There is no dedicated "pending manual judgments" endpoint in Gate/Orca; `ManualJudgments`
  derives it by scanning execution stages for `type == "manualJudgment" && status == "RUNNING"`,
  matching what Deck's own pipeline view does.
- There is no dedicated pipeline-rename endpoint either - front50's `PipelineController` has no
  `/move` route despite Gate's `Front50Service.movePipelineConfig` Retrofit method still existing
  (dead code); rename by fetching a pipeline with `get_pipeline_config`, changing its `name`, and
  saving it back with `save_pipeline_config`.
- **`test_canary_metric_query`**: Deck's canary config editor talks to Kayenta's per-provider
  `fetch/{provider}/query` endpoints (`PrometheusFetchController`, `DatadogFetchController`, etc.)
  directly for its "Test Query" button - Gate never proxied these. `KayentaService.queryMetrics`
  adds a generic passthrough (`@QueryMap` over all provider-specific parameters), since every
  provider's fetch controller shares the same `metricSetName`/`metricName` plus free-form query
  params shape.
- **Global search's ancient multi-type bug is fixed, end-to-end, in Gate itself** (not worked
  around client-side) - verified against clouddriver's actual source, not just Gate's. Gate's
  `/search` REST endpoint used to require exactly one `type` per call
  (`@RequestParam(value = "type") String type`), even though clouddriver's backend
  `SearchController`/`SearchProvider` always accepted a `List<String>` and natively searches
  multiple types in a single pass over its cache (`CatsSearchProvider.findMatches` does one
  combined cache-identifier scan across all requested types, not one scan per type - see its
  source). The break was entirely at Gate's layer: `ClouddriverService.search` (gate-core)
  declared `type` as a single `String`, so a multi-type request could never reach clouddriver as
  multiple values, no matter what a caller sent - fixed by changing `type` to `List<String>`
  end-to-end (gate-core's `ClouddriverService`, gate-web's `SearchService`/`SearchController`,
  and `SecurityGroupService`, the one other gate-web caller of the same Retrofit method).
  `search_infrastructure` now genuinely searches multiple types in one HTTP call - which is also a
  *performance win*, not a risk: one round trip and one permission-check pass instead of N, and no
  more backend cache-scan work than before (each requested type still needs its own cache lookup
  either way). The one behavioral tradeoff to know: a multi-type call shares a single
  `pageSize`/`page` budget across the combined, relevance-sorted result set, so a type with many
  matches can crowd out one with few - `search_all_types` still exists, and still fires one call
  per type, specifically for when you want a fair/independent budget per category instead (this
  also matches Deck's own frontend, which never relied on multi-type-in-one-call either -
  `InfrastructureSearchServiceV2` in deck fires one request per registered search category and
  merges client-side).

  Two related things were investigated and deliberately **not** changed, both upstream in
  clouddriver rather than Gate, and out of scope for this module's blast radius: (1) clouddriver's
  own `SearchController` still declares `type` as required with no default, despite its javadoc
  claiming an omitted type searches everything, so a truly typeless "search absolutely everything"
  still isn't possible - you must supply at least one type; and (2) when a query matches results
  from more than one clouddriver `SearchProvider`, clouddriver merges them into one result set and
  hardcodes its top-level `platform` field to `"aws"` regardless of which provider(s) actually
  matched (see `CatsSearchProvider.getPlatform()`'s own `// TODO(cfieber) - need a better story
  around this`, and the `SearchController` workaround for
  [spinnaker/deck#128](https://github.com/spinnaker/deck/issues/128) - both unresolved upstream as
  of this writing). Don't trust the aggregate `platform` field as "which provider produced these
  results" when more than one could match; prefer per-result fields.

  `SearchToolsWireTest` verifies the actual HTTP request Gate sends (via a real Retrofit client
  against `MockWebServer`, not a mocked interface) matches what clouddriver expects - including
  `searchInfrastructureSendsMultipleTypesAsRepeatedQueryParamsInOneRequest`, the regression test
  for this fix, confirming `type` is sent as repeated query values in a single request rather than
  collapsed to one or split across several. It also confirmed a second bug along the way:
  `Retrofit2SyncCall.execute()` needs `ErrorHandlingExecutorCallAdapterFactory` registered to throw
  on non-2xx responses at all (without it, it silently returns a null body) - the same adapter
  production wiring registers via `ServiceClientProvider`, but easy to omit in a hand-built test
  client and get a false-positive "it works" result.

## Recommended follow-on work

Not built in this version. Roughly in order of likely value for CD-focused MCP clients:

- **Igor build/CI context** (`BuildController`, `/v2/builds`, `/v3/builds`): list build masters,
  jobs, and build history/status - lets an agent check "did the build this pipeline is waiting on
  finish" or trigger context before a deploy.
- **Artifacts** (`ArtifactController`, `/artifacts`): list artifact accounts, resolve available
  versions for a package, fetch artifact content - useful for "what version would this deploy
  actually use" before triggering.
- **Credentials/accounts** (`CredentialsController`, `/credentials`): list configured accounts per
  provider - useful context so an agent doesn't have to guess valid `account`/`region` values for
  the deploy tools.
- Typed deploy/load-balancer tools for Kubernetes and GCP (AWS is the only fully-typed provider
  today; other providers go through `submit_orchestration`).
- `disable_server_group` / `resize_server_group` / `rollback_cluster` tools.
- Security groups (`SecurityGroupController`) CRUD, parallel to `upsert_load_balancer`.
- Entity tags (`EntityTagsController`/`BatchEntityTagsController`) - attach/read arbitrary
  metadata (e.g. "verified", incident links) on a resource; useful for an agent to leave a trail.
- A `spinnaker://tasks/{id}` resource, and `spinnaker://applications/{application}/managed`
  resources mirroring the new `KeelTools` reads.
- An `incident-rollback-runbook` prompt, and a `stuck-rollout-triage` prompt built on `KeelTools`
  (get_managed_environments -> get_environment_constraints -> decide pin/veto/approve).
- Keel's `graphql` passthrough endpoint, `exportResource`/`exportArtifact` (generate a manifest
  from already-deployed infrastructure - useful for onboarding an app onto Managed Delivery), and
  the onboarding/adoption HTML reports were left out of `KeelTools` as lower-value/harder-to-fit
  for a typed tool schema; worth revisiting if onboarding workflows become a priority.
- Per-tool Fiat scopes finer-grained than the blanket `read-only` flag.
- An audit-log resource/sink for MCP-originated actions.
