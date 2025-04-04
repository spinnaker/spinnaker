# Release Management

This document describes the flow of the release process, how all artifacts are versioned, and how those artifacts are assembled into a release.

## Performing a Release

1. Go to the Github Actions UI and select the `Spinnaker Release` action
2. Enter the desired version and any other optional configuration.  
    - If using a standard release number that matches a branch, the source branch will be inferred from that
    - Ensure the `dry-run` setting is the desired value
3. Start the workflow and wait for it to finish

The release workflow performs the following functions:
- Creates and publishes a new Bill of Materials
- Updates and publishes Halyard `versions.yml` with a new entry
- Creates new Docker container tags with the release version
- Triggers a new library publishing job with the release version
- Triggers a new Debian package publishing job with the release versions
- Triggers a new NPM package publishing job with the release version

## Artifacts

The `spinnaker-release` Github Action in this repository is reponsible for creating:

- Release Bill of Materials
- Release tags used for all containers, Java libraries, and Debian packages
- Updating `versions.yml` as used by Halyard
- Creating and uploading release changlogs
- Creating and uploading Github releases

All actual containers and libraries are built at commit-time - this process just assembles those artifacts into a cohesive release.

### Spinnaker Release Versions

Format: `spinnaker-release-<year>.<major>.<build-number>` e.g. `spinnaker-release-2023.1.1` 

There is no concept of a "minor" release.  Compatiblity is intended through all build numbers for the same major release.  

Build numbers are intended to be analogous to semver-style "patch" releases, but may include changes that are semver-style "minor" in scope. 

Each iteration of the normal N-weekly release cycle per year creates a new major version, and does not guarantee compatibility with any previous release.  

### Git Tags

Format: `<project>-<branch>-<build-number>` e.g. `clouddriver-main-9` or `clouddriver-2023.1-9`

Every CI build produces a Git tag for that project e.g. `<project>-<branch>-<build-number>`.  Tags from builds on release branches are shortened by omitting the `release-` portion of the branch name.

Special Git tags also exist for tracking what each project's build number is e.g. `bn-<project>-<branch>-<build-number>`.  To change a project's current build number, just delete the old tag and push a new one.  

### Java Libraries

Format: `io.spinnaker.<project>:<subproject>:<ref>-<build-number>` e.g. `io.spinnaker.clouddriver:clouddriver-aws:main-1` or `io.spinnaker.clouddriver:clouddriver-aws:2023.1-1`

The Spinnaker project makes heavy use of Gradle `java-platform` libraries to define internal dependencies.  These libraries are published with exact versions of every dependency, including sibling projects.  This creates some constraints when using composite builds, since composite builds operate under a single version.  If some jars are not published in a library CI run, their specific versions referenced in `java-platform` POMs will not be present in public repositories, and that version will fail to compile outside the composite build.   

Due to that, all Java libraries are published with any Java build.  This ensures that `java-platform` dependencies are always aligned.  Incrementally publishing libraries is technically possible, but tracking and internally overriding the last published version of every jar, hundreds of times, is not worth the complexity over just publishing all of them every time.

Libraries are published per-branch, with an always-increasing build number.  Libraries are also re-published when a release is created, with that specific release version.

### NPM Libraries

Format: `@spinnaker/core@2023.1.1` or `@spinnaker/core@2023.1.1-0` (prerelease)

In the earliest days, Deck libraries published with an ever-increasing patch build number, e.g. `0.0.661`.  Later, this was changed to an ever-increasing minor build, with the occasional patch, e.g. `0.28.0`.  Neither of these version schemes had any correlation the main project or the contents of the changes within.  

NPM libraries are not published from `main`.  NPM libraries are published from release branches on every commit as a prerelease version.  

In addition to versionsThe Spinnaker release process will [tag](https://docs.npmjs.com/cli/v10/commands/npm-dist-tag) the most recent Deck build from that release branch as the release version

### Docker Containers

Docker format: `<project>:<tag>`, e.g. `clouddriver:main-0` or `clouddriver:2023.1.1`

All main service subprojects publish a container on every commit, for both `main` and `release-` branches.  

### Debian Packages

Debian packages are published with different names for release and continuous builds, as that package management system doesn't work well with many releases.

Note the `-dev` name extension for non-production packages.  Cluttering an `apt` package's namespace is not desirable.  

Deb format (prod): `spinnaker-<service>=2023.1.1`
Deb format (release branch dev): `spinnaker-<service>-dev=2023.1-1`
Deb format (dev): `spinnaker-<service>-dev=1`

As these serve a similar role to containers, they are published at the same cadence.  
