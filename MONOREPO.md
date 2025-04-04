# Spinnaker Monorepo

This summarizes the content, structure, maintenance, and TODOs of this Spinnaker monorepo project

Original RFC: https://github.com/spinnaker/governance/pull/336

See also: [ADOPTION.md](https://github.com/jcavanagh/spinnaker-monorepo-public/ADOPTION.md)

## Links

If you do not have access to any of these things, please DM me!

**Repo:** https://github.com/jcavanagh/spinnaker-monorepo-public  

**Maven:** https://console.cloud.google.com/artifacts/maven/spinnaker-monorepo-test/us-west2/maven?project=spinnaker-monorepo-test

**Apt:** https://console.cloud.google.com/artifacts/apt/spinnaker-monorepo-test/us-west2/apt?project=spinnaker-monorepo-test

**Docker:** https://console.cloud.google.com/artifacts/docker/spinnaker-monorepo-test/us-west2/docker?project=spinnaker-monorepo-test

**NPM:** https://console.cloud.google.com/artifacts/npm/spinnaker-monorepo-test/us-west2/npm?project=spinnaker-monorepo-test

**Nexus:** Coming Soon (TM)

**GCP Project:** https://console.cloud.google.com/home/dashboard?project=spinnaker-monorepo-test

## Overview of Changes

- Gradle 7 upgrade
  - Gradle 8 was attempted, but too much was broken and Kotlin plugins don't support it well (or at all)
  - Gradle 7.6.1 brings us important composite build configuration options, notably the ability to prevent something from being substituted by Gradle and allowing us to override it
  - Old `enableFeaturePreview` declarations removed, as the feature was out of preview
  - Several plugins used by `spinnaker-gradle-project` were upgraded for Gradle 7 compatibility - everything that publishes, mainly
  - Some code was added to specify `duplicatesStrategy` on `Copy` tasks, as Gradle 7 now validates to prevent duplicate resource files on the classpath
- Consoldiation and rework of all Github Actions
  - All workflows were consolidated and refactored for reusability
- Reworking of all versioning and publishing
  - In general, all things now have an associated
    - This is implemented by the `.github/actions/build-number-tag` action
    - Each project + ref combination has its own counter for build artifacts
    - A special `spinnaker` scope exists to coordinate Java library versions
    - The `deck` scope applies a consistent build counter to all Deck packages
  - `main` versioning
    - The `main` branch publishes on an ever-increasing build number
  - `release-*` versioning
    - Release branches will build according to their ref
      - e.g. `release-2023.1.x` generates build versions `2023.1.1` and so forth
      - This is distinct from an BOM release name `2023.1.1`, which may reference many container images e.g. `clouddriver:2023.1.5` and `orca:2023.1.4`, depending on what changes have been made
      - We might consider adjusting this if tagging is confusing
    - Releases (GH/boms/etc) are auto-manual - there is a workflow button to press, but do not happen on commits to release branches
  - All Java libraries must be published on each push, so that `-bom` packages have coherent references to their internal dependencies when published
  - Containers and debs only publish if themselves or a direct dependency changed (e.g. `kork` changes publish everything)
- Liquibase upgrade to 4.3.5 (the version that ships with current Boot)
  - This was a happy accident, as the [liquibase duplicate-files-on-classpath issue](https://github.com/liquibase/liquibase/issues/2818) is actually the same problem as the Gradle 7 upgrade note above
  - This upgrade was required to fix tests failing on MySQL with a `ClassCastException` when running migrations
  - I chose not to adopt the [4.13.0 upgrade PR](https://github.com/spinnaker/kork/pull/970), as I was still able to replicate that issue
  - Various small tweaks to the SQL tests were required after this, but they now all run successfully
- Mass-deletion of unused Gradle wrappers, property pins, .github, .idea, and etc as detailed below

### Feb 2024 Update 
- Java 11 and 17 container publishing for applicable projects
  - The `publish-docker` composite action will auto-detect the presence of Java 11 Dockerfiles and publish as needed
- Most GHA consolidated into custom composite actions over reusable workflows
  - Most of the limitations of composition actions have been fixed by Github over the past several months
    - The current main drawback to composite actions is a lack of direct access to repository `secrets` - they must be passed in as inputs like any non-composite action
    - Issue link: https://github.com/actions/toolkit/issues/1168
  - However, the main limitation of reusable workflows is much harder to deal with - each reusable workflow is a separate job, and additional steps cannot easily be added around it
- Deck publishing flow is now fully integrated
  - No more version bump PRs - prerelease NPM versions are published on every `deck` or `deck-kayenta` publish (see `npm` repo above for examples) 
  - Versions are somewhat synthetic - rewritten dynamically and committed during the build, pre-publish
    - We can change the in-repo committed version to something like `0.0.0` for all packages
    - This may not be ideal - it is likely better to use Lerna versioning and incremental publishing, though its assumptions around tagging and committed values aren't exactly aligned with 
  - Deck package versions are also aligned - all published under the same version, regardless of which packages changed
- Tooling to allow pulling and integrating changes from individual repos via automated or on-demand pull requests to the monorepo
- Tooling to allow a user to port existing individual-repo PRs in a guided fashion\
- Fully automated release tooling via the `spinnaker-release` custom action

#### TODO

- May need to go back to incremental Deck package publishing

## Feedback Requested

- General feedback on versions
  - Maven library versions
  - Deb versions, including new `<project>-dev` main-branch deb builds
  - Container tagging - I may have missed some variants here
    - Elimination of sha + timestamp tags, replaced with build number tags with additional label metadata
- General feedback on GHA workflow structure and implementation
- Deck versions and internal version bumps
  - Just using a single Lerna version for all packages - no bump PRs, everything gets shipped just like Java libraries
- Halyard will now publish on the same release train as Spinnaker
- Halyard compatible versions are no longer referenced in the BOM
  - Users are meant to just use the same Halyard version as the release train
- Default run tasks in root build.gradle have combined output
  - Not really a great way to fix this in Gradle, but still wanted a "just start it" button
  - Still have an initial configuration problem
  - May be better as a docker-compose setup or something
- Cost design of GHA workflows - better to have more complex conditions in steps and fewer jobs?

## Workflow Structure

Workflows are designed with the following goals:

1. Everything should be able to be run manually in a normal fashion if needed
1. Workflows should be specifically scoped when possible, to allow expansion of the monorepo without disruption to existing builds
1. Common functionality should be reused via `workflow_call` or composite actions wherever possible

Three major components are `spinnaker-libraries.yml`, `generic-build-publish.yml` and `version.yml`.

`spinnaker-libraries.yml` is a reusable workflow for publishing all Spinnaker libraries with one coherent version.  This allows `-bom` packages to function, as they all pin versions internally equal to the Gradle `version` set during the composite build.

`generic-build-publish.yml` is a reusable workflow for publishing all artifacts required by JVM service projects.

`version.yml` encapsulates all versioning information, and provides outputs to be referenced by downstream jobs.  Running this more than once in a workflow can be detrimental (double bumping a build number, for instance), so it is important the information is captured once and plumbed through.

## Deletions Due to Obsolescence

- Removed all old Gradle wrappers
- Removed all nested `.github` and `.idea` folders
- Removed all project version pin properties throughout
- Removed old partial-composite build code from build scripts
- Removed all `mavenLocal` enablement code from projects now composite
- Added a root-level `versions.gradle` file to deliver buildscript dependency versions across projects
- Consolidated all `kotlin.gradle` and `kotlin-test.gradle` files
- Split off detekt configuration from `kotlin.gradle` in Kork/Orca, moved remainder to root
- Remove all `defaultTasks` declarations from composites - those have new entry points defined in the root `build.gradle`
- Removed all `deck` scripting around version bumps and bump PRs

## OSS Transition

This repository publishes all artifacts to a parallel GCP set of buckets/GAR repos.

I would recommend it continue to do so until the next release cut, before which time we can validate produced artifacts independently.  After it has been determined that the produced artifacts are correct and good, we can cut over development  the first cut branch would be `release-2023.1`, then builds

There are two ways this repository can be maintained before it is actively producing public artifacts:

### Wipe Clean and Cherry-Pick Conversion

1. Checkout a clean branch with an initial empty commit
1. Run each `git subtree add ...` command from `init.sh` manually
1. Cherry-pick the single monorepo-conversion commit on top of that
1. Force-push the branch

### Integrate Each Subproject Via Subtree Merges

1. Check out a new branch based on the ref you'd like to catch up
1. Run `./pull.sh <project> -r <ref>` and resolve any conflicts
  - The `subtree_pull_editor.sh` script writes a nicer and more helpful commit message, detailing what was merged
  - After resolving conflicts, be sure to use the `git commit -a -F SUBTREE_MERGE_MSG` command to keep the nice commit message, as the script will recommend
1. Repeat for each project
1. Create a pull request
1. Merge the PR with a **MERGE COMMIT**
  - I cannot stress this enough - it must be a **regular merge commit**
    - Squashing/rebase-merging is destructive to Git history that we need to preserve while transitioning
  - History destruction via squash/rebase will make applying patches from individual repositories during transition much more difficult than it needs to be, and will obscure commits/PRs made in individual repos during that time that may need to be referenced

There will be both backports to individual repositories and the monorepo while we are in a transitional state.  We have two choices of how to resolve the transition:

1. Maintain the separate repos until the previous release is no longer supported, then deprecate the individual repositories
2. After the monorepo is stably producing artfacts, import the old ref as a new release branch and re-monorepo-ify it
  - This may require some special GHA workflows to accomodate the old-style "semver" release versioning, but probably not

Option two is likely the least

### Maintenance Scenarios

This section details how to move code bidirectionally between monorepo and individual repos.  This is mostly just lifted from the original RFC for visibility.

#### Integrating Individual Project Changes into a Subtree-ified Monorepo

This is basically the above transition process in more detail. This technique can also be useful for building your own bespoke monorepo of Spinnaker, and perhaps other Spinnaker-related projects or plugins that might benefit from composite builds or colocated code.

1. Add the OSS monorepo to your private individual fork as a remote
    - `git remote add oss git@github.com:spinnaker/spinnaker.git`
    - `git fetch oss`

1. Merge the OSS monorepo branch into your fork with the `subtree` strategy.  **DO NOT SQUASH OR REBASE THESE CHANGES - MERGE COMMIT ONLY!**
    - `git merge -X subtree=<subtree> oss/<branch>`
    - For example, if you have a `clouddriver` fork, and you want to integrate changes from `main`:
      - `git merge -X subtree=clouddriver oss/main`

1. A (mostly) equivalent command is `git subtree pull` or `git subtree merge`, but using that will create multiple "split" points in the tree, and make the history harder to traverse.  It is generally preferable to use `git merge -X subtree=<subtree> ...` instead.

1. Keep in mind that the merged code won't necessarily actually run - the changes could depend on additional changes in other projects, like `kork`.  Repeat the above for all Spinnaker projects that require re-integration.

#### Cherry-picking OSS Monorepo Changes to Private Individual Project Forks

Some fork maintainers may wish to pick and choose which code they take, rather than integrating the OSS branch wholesale.  This process looks a bit roundabout, but works similarly to how `git cherry-pick` operates under the hood.

1. Add the OSS monorepo to your private individual fork as a remote
    - `git remote add oss git@github.com:spinnaker/spinnaker.git`
    - `git fetch oss`

1. Retrieve and apply the diff to your tree with the following command:
    - `git show <commit_sha_to_pick> --no-color -- "<subtree>/*" | git apply -p2 -3 --index -`
      - The pattern matching string in this example is a subtree's folder, but it can be any path(s) if additional filtering is desired

1. This will pipe the diff of your desired change to `git apply`, filtering the files to one project subtree only (` -- "<subtree>/*"`), trimming the file paths in the diff by one additional directory (`-p2`), using three-way merge (`-3`), and updating the index (`--index`).  Modify other options to `git apply` as desired to suit your preferences and workflow.

#### Contributing a Change from a Private Individual Project Fork To the OSS Monorepo

Individual project forks can still contribute changes back to the OSS Monorepo, using a similar diff/apply flow as above, but in the reverse direction:

1. Fork the OSS monorepo on Github, and clone that monorepo fork locally - as one would any project.

1. Add your private individual fork as a remote to the cloned monorepo fork
    - `git remote add <remote_name> <url_to_private_individual_fork>`
    - `git fetch <remote_name> <url_to_private_individual_fork>`

1. Choose a branch name, and check it out using the appropriate remote base branch
    - `git checkout -b <name> <remote_name>/<remote_ref>`

1. Retrieve and apply the diff to your tree with the following command:
    - `git show <commit_sha_to_pick> | git apply --directory <subtree> -3 --index -`

1. This will pipe the diff of your desired change to `git apply`, adding the destination subtree directory prefix (`--directory <subtree>`), using three-way merge (`-3`), and updating the index (`--index`).  Modify other options to `git apply` as desired to suit your preferences and workflow.

1. Push your branch to your Github organization, and open a PR from there as usual

#### Integrating OSS Monorepo Changes into a Private Individual Project Fork

Once the OSS monorepo is the source of truth, private individual project forks will still exist and need to be maintained.

Unfortunately, this process is the most difficult.  There is not a way to cleanly merge from an OSS monorepo to a private individual fork, as changes to the OSS monorepo will include files from other services that do not exist in the destination single-service tree.

My recommendation here is that all private individual forks monorepo-ify themselves, using the creation and import process described elsewhere in this document, and leveraging the OSS composite build process.  Once all of your forks are combined into a private monorepo, the file paths will align and OSS changes can be integrated cleanly.

If you are already wholesale-integrating OSS changes into your forks, you can just import your forks as they are into your new monorepo, then pull from the OSS monorepo directly.

If you are not wholesale-integrating OSS changes from your forks, you can still pick changes from the OSS monorepo to your private monorepo using the standard cherry-pick process.


## TODO

- Rework plugin version compatibility checking against the new versioning system
