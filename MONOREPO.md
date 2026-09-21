# Spinnaker Monorepo

This describes the structure, build/publish mechanics, and history of this Spinnaker monorepo,
for contributors working in this repository. Most of it — the History, Workflow Structure, and
Individual Repo Archival sections — is a historical record of the migration to a monorepo, kept
for context on why the build is put together the way it is. It is not a source of current
project information.

For that, use:

- **Project status, docs, and release info:** https://spinnaker.io
- **Changelogs:** https://spinnaker.io/changelogs/
- **Contribution guidelines:** https://spinnaker.io/docs/community/contributing/

Original RFC: https://github.com/spinnaker/governance/pull/336

The one-time migration from per-service repos to this monorepo is complete; those per-service
repos are now archived and read-only.

## Current State

This is a Gradle composite build (Gradle 9.6.1). The root `settings.gradle` includes:

`clouddriver`, `deck`, `echo`, `fiat`, `front50`, `gate`, `igor`, `kayenta`, `keel`, `kork`,
`orca`, `rosco`, `spin`, and `spinnaker-gradle-project` (plugins, included first).

`deck` and `spin` have their own lifecycle/tooling and are excluded from the root `build`/`test`/
`check` meta-tasks; `spinnaker-gradle-project` is excluded too, since it's plugin tooling rather
than a service. See [CLAUDE.md](CLAUDE.md) for the actual build/test commands per component.

A few other top-level directories live here but aren't part of the Gradle build:

- `examples/` — codelabs and sample solutions
- `spinnaker-kustomize/` — Kustomize base/overlays for deploying Spinnaker itself

**Halyard has been fully removed** from this repo (`#7865`) — Spinnaker no longer ships a
Halyard-managed install path. `spinnaker-release` still generates and publishes a Halyard-format
`versions.yml` for external compatibility, but no Halyard source lives here.

**`deck-kayenta` was folded into `deck`** (`#7853`) and now lives at `deck/packages/kayenta`.

## Links

- **Maven:** Java libraries publish to Maven Central under the `io.spinnaker.<service>`
  namespace, e.g. https://central.sonatype.com/search?q=io.spinnaker.kork
- **Apt:** Debian packages publish to the `spinnaker-community` GCP Artifact Registry project:
  https://console.cloud.google.com/artifacts?project=spinnaker-community
- **Docker:** Container images publish to GHCR under the `spinnaker` GitHub org (GAR-based
  Docker publishing has been retired): https://github.com/orgs/spinnaker/packages
- **NPM:** Deck packages publish to npmjs.org under the `@spinnaker` scope:
  https://www.npmjs.com/search?q=%40spinnaker

## History

### 2023 Migration

- Gradle 7 upgrade
  - Gradle 8 was attempted, but too much was broken and Kotlin plugins didn't support it well
    (or at all) at the time — the build has since moved on to Gradle 9 (see Current State above)
  - Gradle 7.6.1 brought composite build configuration options, notably the ability to prevent
    something from being substituted by Gradle and allowing us to override it
  - Old `enableFeaturePreview` declarations removed, as the feature was out of preview
  - Several plugins used by `spinnaker-gradle-project` were upgraded for Gradle 7 compatibility -
    everything that publishes, mainly
  - Some code was added to specify `duplicatesStrategy` on `Copy` tasks, as Gradle 7 validates
    to prevent duplicate resource files on the classpath
- Consolidation and rework of all GitHub Actions
  - All workflows were consolidated and refactored for reusability
- Reworking of all versioning and publishing
  - In general, all things now have an associated build number
    - Implemented by the `.github/actions/build-tag-number` action
    - Each project + ref combination has its own counter for build artifacts
    - A special `spinnaker` scope exists to coordinate Java library versions
    - The `deck` scope applies a consistent build counter to all Deck packages
  - `main` versioning
    - The `main` branch publishes on an ever-increasing build number
  - `release-*` versioning
    - Release branches build according to their ref
      - e.g. `release-2023.1.x` generates build versions `2023.1.1` and so forth
      - This is distinct from a BOM release name `2023.1.1`, which may reference many container
        images e.g. `clouddriver:2023.1.5` and `orca:2023.1.4`, depending on what changes have
        been made
    - Releases (GH/BOMs/etc) are manually triggered - there's a workflow button to press, but
      releases do not happen automatically on commits to release branches
  - All Java libraries publish on each push, so that `-bom` packages have coherent references to
    their internal dependencies when published
  - Containers and debs only publish if themselves or a direct dependency changed (e.g. `kork`
    changes publish everything)
- Liquibase upgrade to 4.3.5 (the version that ships with current Boot at the time)
  - This fixed tests failing on MySQL with a `ClassCastException` when running migrations,
    caused by the same duplicate-files-on-classpath issue as the Gradle 7 upgrade note above

### Feb 2024 Update

- Java 11 and 17 container publishing for applicable projects
  - The `publish-docker` composite action auto-detects Java 11 Dockerfiles and publishes as
    needed
- Most GHA consolidated into custom composite actions over reusable workflows
- Deck publishing flow fully integrated
  - No more version bump PRs - prerelease NPM versions publish on every `deck` publish
  - Versions are rewritten dynamically and committed during the build, pre-publish
  - Deck package versions are aligned - all published under the same version, regardless of
    which packages changed
- Tooling to pull and integrate changes from individual repos via automated or on-demand pull
  requests to the monorepo (superseded — see [Current State](#current-state); the individual
  repos are now archived)
- Fully automated release tooling via the `spinnaker-release` custom action

### Later Removals

- Halyard removed entirely from the codebase (`#7865`, 2026-07)
- `deck-kayenta` folded into `deck/packages/kayenta` (`#7853`)
- All `git subtree`-era tooling removed: `pull.sh`, `init.sh`, `subtree_pull_editor.sh`,
  `history.sh`, and the `update-monorepo` GitHub Action/workflow (which had never actually run).
  The per-service repos are archived and take no further commits, so there was nothing left to
  pull or bootstrap from.

## Workflow Structure

Workflows are designed with the following goals:

1. Everything should be able to run manually in a normal fashion if needed
1. Workflows should be specifically scoped when possible, to allow expansion of the monorepo
   without disruption to existing builds
1. Common functionality should be reused via `workflow_call` or composite actions wherever
   possible

`spinnaker-libraries.yml` is a reusable workflow (`workflow_call`) for publishing all Spinnaker
libraries with one coherent version. This allows `-bom` packages to function, as they all pin
versions internally equal to the Gradle `version` set during the composite build.

`.github/actions/generic-build-publish` is a composite action for publishing all artifacts
required by JVM service projects.

`.github/actions/version` is a composite action that encapsulates all versioning information and
provides outputs referenced by downstream jobs. Running it more than once in a workflow can be
detrimental (double-bumping a build number, for instance), so it's important the information is
captured once and plumbed through.

There used to also be a `git subtree`-based `update-monorepo` action/workflow for pulling changes
from the individual per-service repos into their matching subtree here. It never actually ran
(zero recorded workflow runs) and the individual repos no longer take direct commits, so it —
along with the root-level `pull.sh`/`init.sh`/`subtree_pull_editor.sh`/`history.sh` scripts it
was built to replace — has been removed. See [Individual Repo Archival](#individual-repo-archival).

## Individual Repo Archival

The per-service repos (`spinnaker/clouddriver`, `spinnaker/orca`, etc.) that this monorepo was
built from are archived and read-only.

If an old, unmerged change from one of those archived repos ever needs to be pulled forward, it
can still be fetched and applied with plain `git`, filtered to the matching subtree path:

```bash
git remote add <name> git@github.com:spinnaker/<service>.git
git fetch <name>
git show <commit_sha> --no-color -- "*" | git apply -p1 -3 --index --directory <subtree> -
```

Adjust `-p`/`--directory` depending on whether the source diff already includes the service
directory prefix. Three-way merge (`-3`) helps `git apply` resolve minor drift since the
repos diverged.
