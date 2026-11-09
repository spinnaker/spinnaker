# Execution summary API + on-demand expansion

**Status**: Proposed - for discussion. Nothing implemented. The new API is alpha, additive, and behind flags (off by default).

| Phase | Description | Status |
|---|---|---|
| 0 | Baseline measurement | Not started |
| 1 | Cheap wins on existing endpoints | Not started |
| 2 | Orca data model (summary column, repository API) | Not started |
| 3 | New Orca/Gate API | Not started |
| 4 | Deck consumption behind flag | Not started |
| 5 | MCP tools | Not started |
| 6 | Pipelines UI redesign | Open - see section 8 |

Update the status table and the relevant phase section as each phase lands.

## 1. Context / problem

Findings below come from reading the code. Payload sizes are not quoted here; the only measurements so far are preliminary figures from one small instance (Phase 0), and the large-install case is still to be measured.

- Deck's 5s poll of in-flight executions uses `expand=true`: up to 30 executions with full stage context/outputs/tasks, overwriting the lighter list items. The 30s history poll uses `expand=false`; `GET /executions` (pipeline/tag filter) omits `expand`, so Gate defaults it to true.
- N+1: `mergeRunningExecutionsIntoExecutions` calls `GET /pipelines/{id}` per active execution missing from the running payload.
- Orca `expand=false` (`TaskController.groovy` `unexpandPipelineExecutions`) strips context/outputs/tasks after the full load and Jackson deserialization of every stage body. It saves wire bytes only.
- A stage body is an opaque JSON blob per stage (`pipeline_stages.body`, optionally in a compressed table), reported up to 1MB+.
- Gate re-serializes untyped `Map<String,Object>`; `GET /pipelines/{id}` has no `expand`; no stage-level GET exists.
- MCP: `get_latest_executions` hardcodes `expand=true`; `get_pipeline_execution` always returns the full execution.
- Deck CPU: JSON-stringify change detection per execution per refresh, in-place mutation, per-card/per-marker timers, per-group guessed-height virtualization, search limited to loaded executions.

Goals: (a) in-flight and list views read a small summary whose size does not depend on what the pipeline carries; (b) stage detail, trigger parameters, artifacts, artifact evaluations and manifests load on demand; (c) Orca does less DB/CPU work, not just less network; (d) MCP/LLM clients get bounded payloads.

Non-goals: changing existing endpoints' default behavior; removing the legacy Deck path in this effort; the Tasks (orchestrations) page, which is a separate initiative (see section 10); extending the entity store (see section 4.1).

## 2. Data flow: today vs proposed

```
TODAY
 Deck --5s--> Gate --> Orca --> SQL: SELECT execution body + ALL stage bodies (context/outputs/tasks)
      <-full-  <-Map-  <-Jackson full-object  (expand=true)
 Deck --30s-> same, then Orca drops context/outputs/tasks AFTER load (expand=false)
 Click card -> GET /pipelines/{id}  (entire execution, every stage)

PROPOSED
 Deck --5s/30s(since=)--> Gate --> Orca --> SQL: execution row + stage `summary` column only
      <-typed DTO,small-  <-typed-  <-ExecutionSummary
 Click stage  -> GET /pipelines/{id}/stages/{stageId}?fields=context,outputs,tasks
 Open parameters/artifacts popup -> GET /pipelines/{id}/trigger?fields=parameters,artifacts,evaluations
 Open artifacts tab -> GET /pipelines/{id}/artifacts   (contents via existing artifact fetch)
 Open manifests    -> GET /pipelines/{id}/stages/{stageId}/manifests (live status via existing /manifests/...)
```

## 3. API reference (alpha)

All endpoints use the same Fiat application-read authorization as their sibling endpoint. Gate flag `gate.execution-summary.enabled` and Orca flag `orca.execution-summary.enabled` (404 when off, which Deck uses to fall back).

### 3.1 List summaries

`GET /applications/{app}/pipelines/summary` (Gate) -> `GET /v2/applications/{app}/pipelines/summary` (Orca)

| Param | Type | Default | Notes |
|---|---|---|---|
| limit | int | existing `gate.execution.fetch.limit` | per pipeline config, as today |
| statuses | csv | all | e.g. `RUNNING,SUSPENDED,PAUSED,NOT_STARTED` |
| pipelineConfigIds | csv | all | |
| ids | csv | - | fetch specific executions (replaces the N+1) |
| since | epoch ms | - | delta: executions updated after this OR still active |
| cursor | opaque | - | keyset on (buildTime,id) for paging |
| pageSize | int | 50 | max 200 |

Response `ExecutionSummaryPage`:

```json
{
  "items": [{
    "id": "01J...", "application": "myapp", "name": "deploy-prod",
    "pipelineConfigId": "a1b2...", "status": "RUNNING",
    "buildTime": 1760000000000, "startTime": 1760000001000, "endTime": null,
    "revision": 1760000042000,
    "canceledBy": null, "cancellationReason": null,
    "limitConcurrent": true, "keepWaitingPipelines": false, "fromTemplate": false,
    "user": "jane@example.com",
    "trigger": {
      "type": "jenkins", "user": "jane@example.com", "dryRun": false,
      "parentExecution": null,
      "buildInfo": {"name": "build-job", "number": 412, "url": "https://ci/job/412"},
      "parameterCount": 1, "artifactCount": 1, "expectedArtifactCount": 0,
      "link": null
    },
    "stages": [{
      "id": "s1", "refId": "1", "name": "Bake", "type": "bake",
      "status": "SUCCEEDED", "startTime": 1760000001000, "endTime": 1760000100000,
      "requisiteStageRefIds": [], "parentStageId": null,
      "syntheticStageOwner": null, "initializationStage": false,
      "group": null, "comments": null, "alias": null, "cloudProvider": "aws",
      "hints": {
        "accounts": ["prod"],
        "labelLines": ["Bake ami-0abc"],
        "failureMessage": null,
        "artifactCount": 1, "manifestCount": 0
      }
    }]
  }],
  "nextCursor": null,
  "serverTime": 1760000050000
}
```

Rules:
- No stage `context`/`outputs`/`tasks`.
- **No trigger parameter values, artifacts, expected-artifact definitions or artifact evaluations (`resolvedExpectedArtifacts`, `boundArtifacts`) in the summary at all.** Only counts. Pipelines are seen with thousands of parameters, or with a handful of parameters holding entire manifests, and with large artifact payloads; a cap that truncates still ships a variable amount of data and still makes the UI render it. The data is loaded on demand from section 3.6 instead.
- `trigger.buildInfo` is limited to `name`, `number` and `url` (each length-capped); the rest of CI build info (SCM lists, test results) is detail.
- `hints` are capped (~512 B per stage) and are built only from an allowlist of fields, never copied wholesale from `context`/`outputs`; an oversized hint is dropped, not truncated.
- `parentExecution` is id+application only.
- The size of an item therefore depends on stage count, not on pipeline content.
- ETag `W/"<maxRevision>-<count>"` supported; `If-None-Match` -> 304.

Delta semantics: the client sends the previous `serverTime` minus ~5s overlap; a periodic full refresh (~5 min) reconciles deletions.

### 3.2 Stage detail

`GET /pipelines/{id}/stages/{stageId}?fields=context,outputs,tasks` (default: all three). 404 if the stage does not belong to the execution; 403 per application permission. Replaces whole-execution hydration.

### 3.3 Artifacts

- `GET /pipelines/{id}/artifacts` and `GET /pipelines/{id}/stages/{stageId}/artifacts` -> `[{ "stageId", "kind": "trigger|resolvedExpected|stageOutput", "name", "type", "reference", "sizeBytes?" }]` (list only).
- Contents: no new endpoint; the UI calls the existing artifact fetch path (own authz/URL restrictions) when the user expands one artifact.

### 3.4 Manifests

- `GET /pipelines/{id}/stages/{stageId}/manifests` -> `[{ "account", "location", "name", "kind", "manifestUri?" }]`. Live status stays on the existing `/manifests/{account}/{location}/{name}`; Deck fetches only for the visible/expanded stage and does not re-fetch on every app refresh.

### 3.5 Changes to existing endpoints (compatible)

- Gate+Orca `GET /pipelines/{id}`: optional `expand` (default `true`).
- Deck sends `expand=false` on `GET /executions` with `pipelineConfigIds`.
- MCP: see section 6.

### 3.6 Trigger detail (parameters, artifacts, evaluations)

`GET /pipelines/{id}/trigger?fields=parameters,artifacts,expectedArtifacts,evaluations` (default: none; the caller asks for what the open popup needs).

- `parameters`: `[{ "name", "valueSize", "value", "valueTruncated" }]`, sorted by name, pageable (`offset`/`limit`, default 100) so a trigger with thousands of parameters does not arrive at once. Values over a cap (default 2 KB) are returned truncated with `valueTruncated: true`; the full value is fetched by `GET /pipelines/{id}/trigger/parameters/{name}` only when the user opens that parameter. This is the "3-4 parameters holding a whole manifest" case: the popup lists the names and sizes, and the manifest is fetched when the user expands it.
- `artifacts` and `expectedArtifacts`: references only, same shape as section 3.3 (name, type, reference or `ref://` URI, `sizeBytes?`). Contents use the existing artifact fetch.
- `evaluations`: for each expected artifact, whether and what it matched or resolved to (`resolvedExpectedArtifacts`/`boundArtifacts`), as references, not contents.
- Same Fiat application-read authorization as `GET /pipelines/{id}`; an execution without these fields returns empty lists, not 404.

Deck shows these in a popup (or similar) opened from the row's "Parameters/Artifacts" affordance, which today renders them inline on the card. The row itself shows only the counts from the summary.

## 4. Backend design

**Orca persistence.** Add a nullable `summary` JSON column to the stage tables (plus compressed-table variants where applicable) via Liquibase, following the existing MySQL/Postgres/MariaDB changelog patterns; written in the same statement as `body`. A NULL `summary` (old rows) falls back to parse-and-project; no mandatory backfill. Add an indexed, DB-assigned `updated_at`/revision on the execution row for `since` and ETag (reuse an existing column if one fits - check first). Cost to measure: write amplification and index cost.

**Repository API** (`ExecutionRepository`): `retrieveSummariesForApplication(app, criteria)` and `retrieveStage(executionId, stageId)` as default methods (full-load + project fallback) so `InMemoryExecutionRepository`, `DualExecutionRepository` and plugin repositories keep working. Models `ExecutionSummary`/`StageSummary` in orca-core. The SQL query must be index-scoped (application/status/config_id) and never scan-then-filter (see CODE_STYLE.md). Redis: HMGET only the needed fields. One shared contract test for SQL and Redis.

**Hints SPI.** A `StageSummaryContributor` produces the `hints` at write time. Defaults cover: deployment-target accounts (today Deck's `accountExtractor` reads `context.clusters`/`credentials`), label lines (jenkins/travis/bake read context/outputs today), failure message (truncated `context.exception`), artifact/manifest counts. Plugins register their own. Fallback: Deck lazily hydrates the stage if hints are absent.

**Gate.** Typed DTOs instead of `Map`, same Fiat annotations, ETag pass-through.

**Alternatives considered for the SQL projection:** (A) load the full body, drop fields in Java (no DB/CPU saving); (B) `readTree` + drop (cheaper, still a full read); (C) SQL JSON functions (dialect-specific, unusable with compressed bodies); (D/E) summary column (chosen). Only (D/E) avoids reading the body at all.

### 4.1 Interaction with the artifact-store entity storage

The existing entity storage in `kork-artifacts` (`artifactstore/entities`, README there) replaces manifests and artifacts in stage context with `remote/map/base64` / `remote/base64` references (`ref://...`) at serialization time, and Deck resolves some of them on demand (`core/src/manifest/storedManifest.ts`, used by `ManifestYaml`, and the `deployManifest` forms and status panels in the `kubernetes` and `cloudrun` packages). It is operator-enabled and already shrinks stored execution size.

How this plan relates to it:

- **Orthogonal, no dependency.** The summary is bounded by construction (section 3.1 rules), so it must not rely on the entity store being enabled. The entity store reduces what Orca persists and loads; the summary column is what stops Orca reading the body for list requests. They solve different problems and compose.
- **Not extended here.** Offloading more fields (for example Clouddriver `kato.tasks[].history`) would cost more UI functionality, since every consumer of those fields must then resolve references. A size-threshold handler was considered and is out of scope for this effort; revisit separately if Phase 0 shows Orca-side cost still dominated by stage-detail payloads.
- **Stage detail returns references.** On installs with the entity store on, section 3.2 returns references inside `context`/`outputs`. Deck needs one shared helper that resolves references when a detail view is opened (generalizing `resolveStoredManifests`), so the raw JSON and execution-details views stop showing opaque `ref://` entries. This is an explicit Deck work item (section 5). References must be fetched through the existing artifact fetch path so its authorization applies.
- **Hint ordering.** The hints contributor runs on the in-memory stage before the serializer hooks replace values with references. A hint that read manifest fields after offload would only see the reference's small `metadata` (kind, name, namespace). A contract test covers a stage with the entity store on and off producing the same hints.
- **Verify before relying on it.** Confirm in `ArtifactDeserializer` and the Orca wiring that serving a stage does not re-expand references (the demo instance returns references from `GET /applications/{app}/pipelines?expand=true`, but that is one observation).

## 5. Deck consumption

Behind `SETTINGS.feature.executionSummary`, falling back to the legacy path on 404.

- One summary datasource replaces `executions` + `runningExecutions`: 30s base poll with `since`, 5s only while running executions exist and the tab is visible, nothing in hidden tabs.
- Id-keyed immutable store with revision-based change detection (removes stringify diffing).
- `ExecutionsTransformer` prefers `stage.hints`.
- Per-stage hydration stored separately from list items so polls cannot wipe it.
- Lazy artifacts/manifests; a single shared ticker for durations; remove the N+1.
- Trigger parameters, artifacts and evaluations move out of the card into an on-demand popup (section 3.6): list names and sizes first, fetch a large value only when it is expanded, page long parameter lists.
- One shared "resolve references on view" helper for stage detail, generalizing `resolveStoredManifests` (section 4.1), so raw JSON and execution-details views resolve entity-store references instead of showing `ref://` entries.
- A paged, polled list datasource and a lazy JSON viewer will be extracted as shared Deck primitives only if that turns out to be natural while building this; the Tasks page (section 10) is a separate initiative and is not a reason to design for it up front.

## 6. MCP

- `get_latest_executions`: summary by default, optional `includeStages`.
- `get_pipeline_execution`: `view=summary|full`, `stageId`.
- New `get_stage_details` (`fields`, size cap with truncation notice).
- Manual-judgment tools: summary list, then detail only for pending MJ stages.

## 7. Phases

- **0 Baseline:** fixtures (50 executions x ~20 stages; 10 in-flight with 1MB+ bodies; k8s manifest-heavy); Orca/Gate/Deck metrics; acceptance table (e.g. 5s poll < 50KB for 30 running executions). Additions:
  - **Large-scale fixture:** 50+ pipelines, 50-stage executions, manifest-heavy stages, triggers with thousands of parameters, triggers with a few parameters whose values are entire manifests, large artifact lists with embedded manifests, and artifact evaluations (`resolvedExpectedArtifacts`/`boundArtifacts`). Measure the 5s running poll and the 30s list, per `expand` value and summary vs legacy, on the wire (gzip) and decoded, plus Orca time/DB rows and Deck main-thread time (parse, `stringVal`, transform, render).
  - **Entity store on vs off:** run every fixture with `artifact-store` enabled and disabled to quantify its effect on stored size, Orca load/parse time and response size, and to confirm references are not re-expanded when stages are served (section 4.1).
  - **Preliminary data (one small instance, 2026-11-09, `demo` app, entity store on):** 13 executions / 96 stages, `limit=30&expand=true` = 567 KB decoded vs 48 KB with `expand=false` for 12 executions (about 10x, gzip about 8x on both); stage `context` 62%, `tasks` 21.5%, `outputs` 6.6%, with 166 `remote/map/base64` references already in the payload. The largest remaining item was Clouddriver `kato.tasks` (118 KB for `deployManifest`, about 79 KB of it `history`). Latency differed by only 10-15 ms at this size, so the cost here is client CPU and bytes, not Orca time; this does not yet reproduce the slowness seen on large installs, which is what the large-scale fixture is for.
- **1 Cheap wins:** Deck running poll -> `expand=false` with a non-clobbering merge; `expand=false` on `/executions`; remove the N+1; `expand` on `GET /pipelines/{id}`; verify compression Orca->Gate->client; MCP `expand` default false; Redis stage-field skipping; benchmark `optimizeExecutionRetrieval`.
- **2-3** Orca model + API, **4** Deck, **5** MCP, as above.
- **Tests:** SQL (MySQL/Postgres testcontainers) + Redis contract suite (summary == projection of full, `since`, cursor stability, NULL-summary fallback, compressed tables, hint caps, hints identical with the entity store on and off, summary size independent of trigger parameter/artifact volume, trigger detail paging and truncation); Gate authz (403, 304, headers); Deck merge/Profiler tests; payload-size regression tests with large fixtures; end-to-end flag on/off comparison against Phase 0.
- **Risks:** hint drift for plugin stages (SPI + lazy fallback); write amplification; online DDL on big stage tables; clock skew for `since` (DB-assigned time + overlap); authz regressions on new endpoints; Deck/Gate/Orca version skew (flag + 404 fallback).

## 8. Pipelines UI - for broad discussion (no decision)

Current pain: variable-height cards; the stage marker bar conveys little without hover/click; detail is embedded in the card; no real pagination; search only sees loaded executions; grouping/filtering is hard to follow; failures and "what needs me" are hard to spot.

The wireframes below are ASCII, drawn from a screenshot of the current UI (the `demo` application). They are wireframes of proposals, not screenshots of built UI.

**Current layout**

```
+ SPINNAKER  Search Projects Applications Pipeline Templates         user v  [Search] +
+--------+---------------------+--------------------------------------------------------+
| demo   | Filters       [<<]  | [+][-] Group by [Pipeline v] Show [2] executions per    |
|PIPELINES| [Search by field]? |   pipeline  [x] stage durations   [Create][Configure][Start Manual Exec]
|CANARY.. | PIPELINES          | v [K8S-NAMESPACE-PROD] Deployment    Trigger:enabled  Configure  Start Manual
|CLUSTERS | [ ] Deployment     |   +----------------------------------------------------------------+
|LOAD BAL | [ ] Run curl..     |   | MANUAL START   |00:01|00:07|00:07|00:06|05:26|05:03|..|00:07|  |
|FIREWALLS| [ ] Simple         |   | jason@..       |=====================================|  Dur 05:42 |
|KUBERNETES [ ] canary show    |   | 3 days ago     Status: SUCCEEDED              [retry][trash]|
|FUNCTIONS| [ ] Bake Manifest  |   | > View All Parameters/Artifacts (1/1)                          |
|TASKS    | [ ] Test[example]  |   | > Execution Details                                            |
|CONFIG   | [Reorder Pipelines]|   +----------------------------------------------------------------+
|         | STATUS             |   | MANUAL START   |00:00|00:07|00:07|00:06|[red 00:00]|05:00|- - - -| TERMINAL
|         | [ ] Running        | > Run curl on demo   (collapsed group; one header row each)
|         | [ ] Awaiting Judg. | > Simple   > canary show   v Bake Manifest Example ...
|         | [ ] Terminal ...   |
+--------+---------------------+--------------------------------------------------------+
```

What this makes concrete (each card repeats it every row): a long stage bar where each segment shows only a duration (no stage name until hover), a second "Status: X / Duration: Y" line, a left trigger block ("MANUAL START / user / age"), two disclosure links per card, and per-card retry/delete icons. A failed stage is identifiable only by a red segment. Collapsed groups hide everything, including a pipeline that is currently failing. Filters (status and pipeline checkboxes) are a long left rail separate from the content.

**A. "Needs attention" lane + list (same chrome, keeps left rail and group headers)**

```
+--------------------------------------------------------------------------+
| demo    Group by [Pipeline v]  Show [2] per pipeline  [x] stage durations |
+--------------------------------------------------------------------------+
| NEEDS ATTENTION (3)                                                      |
| ! Deployment #412   Awaiting judgment @ "Approve"   2h   [Judge] [Open]  |
| > Bake Manifest #9  Running 4m  [Bake ok][Deploy ....][Verify .]          |
| x Deployment #411   Failed @ "Deploy": quota exceeded    [Retry] [Open]  |
+--------------------------------------------------------------------------+
| v [K8S-NAMESPACE-PROD] Deployment        2 recent: 1 ok, 1 failed        |
|   #412 o paused  jason  manual  3d   [#####o....]                         |
|   #411 x failed  jason  manual  3d   [##x.......]  "quota exceeded"       |
```

Collapsed group headers also show a health summary ("2 recent: 1 ok, 1 failed") so failure is visible without expanding. Data source: summary `status`, stage statuses, and `hints.failureMessage`.

**B. Dense table (inline expand) - same data as the cards, one line each**

```
| Status | Pipeline    | Account/NS        | Trigger       | Started | Dur  | Stages (named on hover)   |
| RUN    | Bake Manif. | -                 | manual jason  | 2m ago  | 2m   | [ok][ok][>.][..]          |
| JUDGE  | Deployment  | K8S-NAMESPACE-PROD| manual jason  | 3d ago  | 3d   | [ok][ok][J][..]           |
| FAIL   | Deployment  | K8S-NAMESPACE-PROD| manual jason  | 3d ago  | 0:15 | [ok][ok][ok][x][-] quota  |
|  v Deployment #411  Failed at "Deploy": quota exceeded     [Open stage] [Restart stage] [Params/Artifacts(1)] |
```

Params/artifacts and execution details move into the inline expansion and load on expand (stage detail, artifact list).

**C. Master/detail split with deep link**

```
+-- executions ----------------+-- deploy-prod #412 ---------------------+
| > deploy-prod #412  judge    |  Bake ──> Deploy ──> [Judge] ──> Verify |
|   deploy-stg  #98  running   |  Stage: Judge (selected)                 |
|   build-img   #77  failed    |  Instructions ... [Continue] [Stop]      |
|   ... (virtualized, paged)   |  Artifacts(2)  Manifests(3)  JSON        |
+------------------------------+------------------------------------------+
```

**D. Stage timeline (gantt)**

```
Bake    |#########|
Deploy            |##########......|   (parallel stages stack vertically)
Judge                             |~~~~~~~~ waiting 2h
Verify                                              |..|
        0m        5m        10m       15m
```

Foundation work regardless of look: windowed list, cursor paging, server-side status/pipeline filters (already parameters of section 3.1), failure summary on the row from `hints.failureMessage`. Free-text server-side search is deliberately not part of this list: see the open question below.

Proposed next step: prototype A+B+C against the summary API with the flag on, collect feedback, then choose; coordinate with other UI initiatives that should be bundled.

## 9. Open questions

- Which databases do deployments use (MySQL/Postgres/Redis)?
- Is SQL compression enabled in key environments (affects any SQL JSON approach)?
- Is plugin stage UI in scope for the hints SPI?
- Delta polling in the first cut, or ETag only?
- Staging environment for the Phase 0 baseline, or synthetic fixtures?
- Is server-side search needed at all? Search today only covers loaded executions, which is mostly a symptom of loading heavy payloads. With summaries small enough to load far more of them (cursor paging plus server-side status/pipeline filters), client-side search over loaded summaries may cover most needs. Decide from Phase 0 numbers and Phase 4 usage rather than building it up front. If it is needed later: parameterized, index-scoped queries only, no load-then-filter, and no free-text search over `context`/`outputs`.
- What should the trigger popup do for parameter values that are themselves manifests (render as YAML, download, copy)? Does it share the lazy JSON/YAML viewer used for stage detail?

## 10. Related work

- **Tasks (orchestrations) page**: [spinnaker/spinnaker#8188](https://github.com/spinnaker/spinnaker/issues/8188) proposes a paged, polled, lazily-loaded rework of the application Tasks page (25 per page, active tasks polled faster than the first page, status filter on the server, JSON tab loaded when opened). It has the same overfetch problem as pipelines and lists a lighter list payload as optional later work. It is **a separate initiative** with its own plan and PRs and is not planned or in progress yet. This plan does not cover orchestrations. When the Tasks work is planned, it should reference this document, and the two can share a paged/polled list datasource and a lazy JSON viewer if that proves natural; neither depends on the other.
- **Artifact-store entity storage** (`kork-artifacts`): see section 4.1.
