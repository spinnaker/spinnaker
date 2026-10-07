# Remove Titus from Spinnaker

Status: **In progress** on branch `removeTitus`. One PR across the monorepo. Started 2026-09-26.
Owner: Jason McIntosh
Related: [keel-modernization.md](keel-modernization.md) (this is its WS0.1 prerequisite)

## Why

Netflix's Titus container platform is archived as open source. There is no OSS Titus control plane to deploy to, and no way to test these code paths. The code still costs every build and upgrade:

- a gRPC/protobuf toolchain in clouddriver pinned to `com.netflix.titus:titus-api-definitions:0.0.1-rc71`
- ~18k lines of clouddriver provider code
- an 8.4k-line Deck package
- Titus-specific branches in orca, keel and shared code
- a Titus type in orca's **plugin API** (`TitusPreconfiguredJobProperties`)

Removing it in **one PR** keeps the repo consistent: no service advertises a provider another service can't talk to. It also gives release notes a single "Titus removed" entry.

## Inventory (2026-09-26)

376 files reference `titus` outside `node_modules`/`build`. Grouped by action:

### Delete whole modules

| Module | Size | Notes |
|--------|------|-------|
| `clouddriver/clouddriver-titus` | 200 files, ~17.8k LOC | gRPC/protobuf plugin setup, `titus-api-definitions`, `grpc-netty-shaded` |
| `orca/orca-clouddriver-provider-titus` | 6 main classes | `TitusServerGroupCreator`, `TitusJobRunner`, `TitusDeployStagePreProcessor`, `TitusRunJobStageDecorator`, `TitusAmazonServerGroupCreatorDecorator`, `TitusInterestingHealthProviderNamesSupplier` |
| `deck/packages/titus` | ~8.4k LOC, 77 files | `@spinnaker/titus` workspace package |
| `keel/keel-titus-api`, `keel/keel-titus-plugin` | ~3k LOC main, 3.5k test | Holds keel's only verification/post-deploy implementations (see "Salvage") |

### Build wiring

| File | Change |
|------|--------|
| `clouddriver/settings.gradle:38` | drop `cloudProviderProjects.put('titus', …)` |
| `orca/settings.gradle:30` | drop `orca-clouddriver-provider-titus` |
| `orca/orca-web/orca-web.gradle:36` | drop project dependency |
| `keel/settings.gradle`, `keel/keel-web/keel-web.gradle`, `keel/keel-scm/keel-scm.gradle`, `keel/keel-test/keel-test.gradle` | drop modules and deps |
| `deck/packages/app/package.json`, `deck/packages/app/src/app.ts` | drop `@spinnaker/titus` dependency and import |
| `deck/tsconfig.json:46-48` | drop path aliases |
| `deck/scripts/buildModules.js:37` | build only `ecs` in that group |
| `deck/karma.conf.js`, `deck/karma-shim.js` | drop titus test globs |
| `deck/pnpm-lock.yaml` | regenerate with `pnpm install` |

### Titus-only features in shared modules: remove

| Location | What | Notes |
|----------|------|-------|
| `orca-clouddriver/.../pipeline/servergroup/UpsertDisruptionBudgetStage.java`, `tasks/servergroup/UpsertDisruptionBudgetTask.java` | Disruption budgets | Only `clouddriver-titus` implements the operation |
| `orca-clouddriver/.../pipeline/job/UpdateJobProcessesStage.java`, `tasks/job/UpdateJobProcessesTask.java` | Job process updates | Only `clouddriver-titus` implements it |
| `clouddriver-core/.../orchestration/AtomicOperations.java` | operation name constants for the two above | Remove the constants |
| `orca-api/.../preconfigured/jobs/TitusPreconfiguredJobProperties.java` | **Plugin API type** | Breaking for plugins that use it. See "Compatibility". |
| `orca-clouddriver/.../config/JobConfigurationProperties.java` | `job.preconfigured.titus` list | Keep `kubernetes` only |
| `orca-clouddriver/.../service/JobService.java:52` | merges Titus preconfigured jobs | remove |
| `orca-plugins-test` (`PreconfiguredJobConfigurationProviderExtension.kt`, `preconfigured.yml`, `OrcaPluginsTest.kt`) | sample plugin uses the Titus type | Rewrite the sample on `KubernetesPreconfiguredJobProperties` |
| `orca-api/src/sample/...` (`PreconfiguredJobStageSample.java`, `treasure.yml`) | API sample | Rewrite on Kubernetes |
| `orca-dry-run/.../TitusBakeOutputStub.kt`, `TitusRunJobOutputStub.kt` | dry-run stubs | delete |
| `deck/packages/core/src/pipeline/config/stages/preconfiguredJob/PreconfiguredJobExecutionDetails.tsx` | Titus branch | remove branch |
| `deck/packages/app/src/settings.js:281` | `providers.titus` defaults (`titustestvpc`) | remove |
| `deck/packages/core/src/managed/resources/resourceRegistry.ts` | `titus/cluster` kind | remove |
| `deck/test/functional/cypress/integration/titus/` | e2e spec | delete |

### Provider allow-lists and special cases: edit

| Location | What |
|----------|------|
| `orca-core/.../pipeline/model/PipelineBuilder.java:89` | `["aws","titus"]` → `["aws"]` |
| `orca-core/.../pipeline/ExecutionLauncher.java:299` | `aws`/`titus` provider check |
| `orca-clouddriver/.../kato/pipeline/ParallelDeployStage.groovy:141` | `['aws','titus']` |
| `orca-clouddriver/.../kato/tasks/rollingpush/DetermineTerminationCandidatesTask.groovy:48` | Titus id workaround |
| `orca-clouddriver/.../tasks/servergroup/SpinnakerMetadataServerGroupTagGenerator.java:124` | comment |
| `orca-clouddriver/.../tasks/job/WaitOnJobCompletion.groovy:65` | comment referencing `TitusJobRunner` |
| `orca-applications/.../utils/ApplicationNameValidator.groovy:33` | `titus` name constraint |
| `orca-bakery/.../api/BakeRequest.groovy:98` | `titus` in `CloudProviderType` enum. Removing it breaks deserialising old bake stage contexts that say `titus`; keep it as deprecated or map it to `docker`. |
| `orca-bakery/.../tasks/CreateBakeTask.groovy:181` | `titusBakeStage.js` workaround comment |
| `orca-validation/src/main/resources/schemas/*.json` (7 files) | `titus` in `cloudProvider` enums |
| `orca-web/config/orca.yml:117` | commented example |
| `clouddriver/cats/cats-sql/.../SqlProviderCache.kt:335` | `titusstreaming` agent special case |
| `clouddriver/cats/cats-sql/.../cache/SqlCache.kt:1112-1115` | `titusagent` application special-case SQL |
| `clouddriver/clouddriver-docker/.../DockerRegistryImageLookupController.java:26` | `/titus/images` alias path. Keep one release as deprecated? Deck's titus package is the only known caller. |
| `clouddriver/clouddriver-aws/.../AbstractClusterCleanupAgent.java:27` | comment ("AWS and Titus subclass") |
| `clouddriver/clouddriver-eureka` (`EurekaInstance.groovy`, `Metadata.groovy`, README) | Titus instance metadata fields |
| `clouddriver/clouddriver-api/.../CloudProvider.java:24`, `clouddriver-core/.../model/Instance.java:82` | javadoc examples |
| `kork/kork-web/.../selector/ByCloudProviderServiceSelector.java:40` | javadoc example |
| `deck/packages/amazon/.../TargetTrackingAdditionalSettings.tsx` | Titus branch |
| `deck/packages/kayenta/.../kayentaStageConfig.model.ts` | Titus reference |
| `deck/packages/eslint-plugin/rules/import-sort.ts` | `@spinnaker/titus` in package ordering |
| keel (non-Titus modules) | see [keel-modernization.md §WS0.1 inventory](keel-modernization.md#ws01-titus-removal-inventory-keel-side) |

### Tests and fixtures to re-point (not delete)

Several are generic provider tests that happen to use `titus` as a sample string:
- clouddriver: `clouddriver-web` `ApplicationsControllerSpec`, `clouddriver-elasticsearch` entity-tags specs
- orca: `PreconfiguredJobStageSpec`, `BulkDestroyServerGroupTaskSpec`, `JobServiceSpec`, `CaptureParentInterestingHealthProviderNamesTaskSpec`, `SpinnakerMetadataServerGroupTagGeneratorSpec`, `UpsertDisruptionBudgetTaskSpec` (delete with the task), `OperationsControllerSpec`, `TitusPreconfiguredJobPropertiesSpec` (delete)
- `orca/orca-keel/src/test/resources/trigger.json`
- kork: `ExceptionSummaryServiceSpec`, `SelectableServiceSpec`
- gate: `InsightConfigurationSpec`
- deck core specs: `ProviderSelectionService.spec.ts`, `PipelineRegistry.spec.ts`, `cluster.service.spec.ts`, `ApplicationWriter.spec.ts`, `AccountSelectInput.spec.tsx`

Swap in `aws` or `kubernetes` so the tests keep covering the generic behavior.

### Leave alone
- `deck/packages/*/CHANGELOG.md`: historical record.

## Salvage before deleting

Keep the **contracts**, not the code, for later keel work:
- `keel-titus-plugin/.../verification/TestContainerVerificationEvaluator.kt` and `LinkStrategy`/`OrcaLinkStrategy`: the "run a container with artifact/env context, report pass/fail, link to logs" contract. It becomes the generic Orca run-job/pipeline verification in keel WS6.3.
- `keel-titus-plugin/.../postdeploy/TagAmiHandler.kt`, `PromoteJarHandler.kt`: the post-deploy action contract.
- Clouddriver `clouddriver-titus` scaling-policy and disruption-budget models: reference only, in case ECS needs similar shapes.

Record these in the keel plan (done: keel-modernization WS6.3) and tag the last commit that contains the code in the PR description, so it can be looked up later.

## Compatibility and data

| Area | Impact | Handling |
|------|--------|----------|
| Clouddriver accounts config `titus.enabled` / `titus.accounts` | Ignored after removal | Release note. Unknown properties are ignored by Spring, so there's no startup failure. |
| Clouddriver SQL cache | Titus agent rows (`cats_v1_*` with titus agent types) become orphaned | Harmless. Optionally document a cleanup SQL or let cache eviction handle it. |
| Front50 pipelines with Titus stages (`deploy` with `cloudProvider: titus`, `runJob` Titus, `upsertDisruptionBudget`, `updateJobProcesses`) | Fail at runtime with an unknown stage / no provider | Release note. Optional front50 read-only report listing pipelines that still reference Titus. |
| Orca plugin API: `TitusPreconfiguredJobProperties` | Plugins that construct it fail to load | Breaking change in the release notes. Alternative: keep the class for one release as `@Deprecated` with no consumers, then delete. **Decision needed.** |
| Orca `job.preconfigured.titus` config | Ignored | Release note |
| Keel DB rows of kind `titus/cluster@v1` | `ResourceFactory.create` fails, and the row is flagged `IGNORE=1` with status Error | Liquibase changeset to delete Titus resources and their environment/artifact links (keel side) |
| Deck `settings.js` `providers.titus` | Removed | Custom `settings-local.js` overrides that set it are ignored |
| `/titus/images` clouddriver alias | 404 after removal | Release note. No known OSS caller once Deck's package is gone. |

## Validation

- `./gradlew :clouddriver:build :orca:build :keel:build :kork:build :gate:build`, and `./gradlew spotlessApply` on touched builds.
- Clouddriver with `includeCloudProviders=all` and with a narrow set (e.g. `aws`) to confirm `settings.gradle` still resolves.
- Deck: `./gradlew :deck:test` (per repo memory: TypeScript compile/tests via Gradle), `pnpm lint` from `deck/`.
- Grep gate: `rg -il titus --glob '!**/CHANGELOG.md' --glob '!plans/**'` should return nothing except intentional deprecations.
- CI fan-out: per `.github/dependencies.yml`, kork changes fan out to every service. Expect a full CI run.

## Decisions (2026-09-26)

Titus is dead in the OSS world, so everything is a hard removal. Nothing is deprecated for a release.

1. `TitusPreconfiguredJobProperties` is **removed** from `orca-api` (plugin API break, called out in release notes). The
   orca-api sample and `orca-plugins-test` now show plugins defining their own `PreconfiguredJobStageProperties`
   subclass, so the extension point keeps an in-tree example.
2. The `/titus/images` alias is **removed** from clouddriver-docker. Deck's Titus package was the only caller.
3. `BakeRequest.CloudProviderType.titus` is **removed**.
4. No front50 report; the release note covers it.

## Implementation notes (things the inventory above missed)

- **keel `TagAmiHandler` moved, not deleted.** It lived in the Titus plugin but only tags EC2 AMIs (`kind.group == "ec2"`).
  It now lives in `keel-ec2-plugin` (`com.netflix.spinnaker.keel.ec2.postdeploy`), with the Orca link and `TASKS`
  helpers inlined. `PromoteJarHandler` ran a Titus container and was deleted, along with `PromoteJarPostDeployAction`.
- **keel stored data.** `keel-sql` changelog `20260926-remove-titus-resources.yml`:
  - deletes `titus/*` resources (mirrors `SqlResourceRepository.delete`; the remaining tables cascade);
  - removes **only** `test-container` verification elements and `promote-candidate-jar` post-deploy elements from
    `environment` / `preview_environment` JSON. Other entries, including plugin-provided ones, are kept in order.
    MySQL 5.7 has no `JSON_TABLE`, so this is a bounded, repeated single-element `json_remove`;
  - a follow-up changeset **halts** the migration (Liquibase precondition) if any such entries remain, so a failure
    is loud rather than data being lost silently.
  - An earlier draft cleared the whole `verifications` array. That was **not** Titus-specific (it would wipe
    plugin or future verification types in the same environment) and was replaced.
- **keel-scm** was getting `keel-ec2-api` transitively through `keel-titus-api`, so it now declares it directly.
- **keel-docker** stays (it's generic container-image code), but it now has no in-tree consumer. Revisit when ECS/k8s land.
- **keel `TargetTrackingPolicy.scaleIn/OutCooldown`** (documented as "Titus only") are removed. Stored specs still load
  because keel's mapper ignores unknown properties.
- **Deck amazon `TargetTrackingAdditionalSettings`**: the `cooldowns` prop and UI were only turned on by the Titus
  package (amazon passes `false`), and the help text lived in Titus. Both are removed.
- **Deck `PreconfiguredJobExecutionDetails`**: the Titus log viewer (`TitusExecutionLogs`, exported from core) is removed.
- **clouddriver-eureka** `titusTaskId` / `titusStack` metadata is removed. The app-from-ASG logic behaves the same for
  non-Titus registrations (those fields were always null).
- The Deck `angular-removal-guard.test.js` keeps `TITUS_MODULE` / `TITUS_REACT_MODULE` in its forbidden-identifier
  list on purpose.
- `keel/docs/preview-environments.md` keeps its Titus examples as a historical design doc, with a banner added.
- **clouddriver `settings.gradle`**: the `titus` provider group was the only thing that included `clouddriver-docker`
  (the Docker Registry provider, which cloudfoundry depends on). Added a standalone `docker` group and listed Docker
  under `cloudfoundry`, so the default `all` build includes the same modules as before.
- **Deck rxjs hoisting**: `rxjs-compat@6.6.7` imports `rxjs` without declaring it, so it used whichever `rxjs` pnpm
  hoists. With the Titus package (an rxjs 6.6.7 consumer) gone, pnpm hoisted inquirer's rxjs 7.8.2 instead, which broke
  kayenta's rxjs-5-style imports (9 TS errors in `epics.ts`). Fixed properly with `packageExtensions` in
  `pnpm-workspace.yaml`, pinning `rxjs-compat` to rxjs 6.6.7, rather than relying on hoisting.
- **Deck specs**: tests that used `titus` as a sample provider switched to a neutral `containerprovider` (registered
  in `SETTINGS.providers` for the test, because `CloudProviderRegistry.registerProvider` ignores unknown providers). Using
  `ecs` instead clobbered the real ECS registration in the shared karma registry.

## Changelog
- 2026-09-26: Inventory and plan split out from keel-modernization.
- 2026-09-26: Implementation started; decisions and implementation notes recorded.
