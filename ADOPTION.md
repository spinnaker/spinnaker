# Monorepo Adoption Steps

## Creation

### Setup `spinnaker/spinnaker` Repository

1. Clean out all existing branches, refs, tags, releases, etc
1. Setup reasonable branch protection rules, broading matching individual repos
1. Setup GHA secrets in the new repository
    - `GAR_JSON_KEY` (general GAR/GCS access)
    - `GRADLE_PUBLISH_KEY` (`spinnaker-gradle-project` publishing to Gradle plugin portal)
    - `GRADLE_PUBLISH_SECRET`
    - `NEXUS_USERNAME` (general Java library publishing to Nexus)
    - `NEXUS_PASSWORD`
    - `NEXUS_PGP_SIGNING_KEY`
    - `NEXUS_PGP_SIGNING_PASSWORD`
    - `NPM_AUTH_TOKEN` (UI library publishing to `npmjs.org`)
    - `GAR_NPM_PASSWORD` (UI library publishing to GAR `spinnaker-monorepo-test`)

## Migration

All Spinnaker repos have open PRs which need to be migrated.  

There isn't a feasible automated PR migration path when most PRs are made from forks owned by others, so the below process should be used.  

### Pull Requests

1. No new PRs to `master` on individual repositories
    - Maintenance PRs to older releases are allowed
    - Contributors must make new contributions to the monorepo.  Repo README's should be updated to this effect.  
1. An archival date for the individual repositories should be determined as per community needs, and take place after all PRs are closed or merged.  
1. Stale PRs should be closed with instructions on how to port the change to the monorepo.  Monorepo includes tools to 

    Suggested text as follows:

    ```
    This repository will be archived in favor of a monorepo located at [`spinnaker/spinnaker`](https://github.com/spinnaker/spinnaker).

    In order to convert this pull request to the monorepo, follow these steps:

    1. Fork the monorepo, then clone your fork locally
        - From the Github UI, fork: https://github.com/spinnaker/spinnaker 
        - Run: `git clone https://github.com/<your-namespace>/spinnaker.git`
    1. Create a new branch in the monorepo clone for your converted PR
        - Be sure this new branch is based on the same branch name as your original PR's target
        - Run: `git checkout -b <branch-name-here> <monorepo-branch-basis-if-needed>`
    1. Add your single-repository fork as a remote to the monorepo fork and fetch
        - Run: `git remote add <remote-name> <url-to-your-original-single-repo-fork> && git fetch --all`
   
    If your original PR is just one commit (or a small number of non-merge commits), cherry-picking into the new subtree is easiest:
   
    1. Cherry-pick each commit from your PR's original branch into the monorepo feature branch, choosing the appropriate target subtree
        - The subtree name will match the original repository's name, e.g. `spinnaker/clouddriver` is in subtree `clouddriver`
        - Be sure to cherry-pick from oldest to newest if your original PR has multiple commits
        - Run: `git cherry-pick --strategy=subtree -X subtree=<subtree> <commit>`
   
    If your original PR has a complex history or catchup merges that, try condensing it into one squashed diff and committing:
   
    1. Merge your original PR's contents from the remote as one commit into the new monorepo branch, under the proper subtree
        - The subtree name will match the original repository's name, e.g. `spinnaker/clouddriver` is in subtree `clouddriver`
        - Run: `git merge --squash --strategy=subtree -X subtree=<subtree> <remote-name>/<original-pr-branch-name>`
        - Run: `git commit`, and create a new commit title and description from the original PR's information
    ```

### Issues

1. Issues are already in `spinnaker/spinnaker`, so no migration is necessary.  

### Cut Over Artifact Production

1. Optionally publish a few artifact runs to `spinnaker-monorepo-test` with the repo in its new location
1. Replace all `spinnaker-monorepo-test` GAR repository references with `spinnaker-community` repository paths, and update GAR credentials to match
1. Re-enable Nexus publishing for Java libraries
1. Replace all GAR NPM publishing with `npmjs.org` publishing

## Maintenance of Individual Repositories

Individual repositories will still need some bugfix changes from the monorepo backported to older releases, while they are still supported. 

- Mergify may be able to do this?  Needs investigation
  - At a minimum, a human can backport a change to an individual repo's `master` branch, and the mergify flow can backport it to individual-repo release branches and project dependencies
- Otherwise, using the following generate-patch-and-apply method will rewrite the file paths to apply to an individual repo (also documented in MONOREPO.md)
  - Clone individual repo, make a feature branch
  - `git show <commit_sha_to_pick> --no-color -- "<subtree>/*" | git apply -p2 -3 --index -`
  - Push branch and open PR
- The above could be automated from the monorepo's GHA workflows with a small level of effort

## Future

- Consider integrating `spinnaker/spinnaker.io` into the monorepo.  This would make documentation updates easier, and simplify the release process.  
  - As there is only one website, this project would only publish itself from the `main` branch
  - New opportunities for documentation generated directly from the codebase, which could greatly improve documentation accuracy without much additional labor.  
