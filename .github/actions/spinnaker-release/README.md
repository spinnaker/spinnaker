# Spinnaker Release Action

Github Action for generating a Spinnaker release

This uses TypeScript, and the output must be committed with each code change so Github can run it.  Github does not rebuild custom Actions.

Before committing a code change, run `npm run build`.

# Usage

GHA: Run the `Spinnaker Release` workflow from the Actions UI, and choose inputs

Local: Run `./local-action.sh` with prepended environment variables to supply inputs

# Sample

This is a sample Spinnaker BoM from release `1.31.2`:

```yaml
artifactSources:
  debianRepository: https://us-apt.pkg.dev/projects/spinnaker-community
  dockerRegistry: us-docker.pkg.dev/spinnaker-community/docker
  gitPrefix: https://github.com/spinnaker
  googleImageProject: marketplace-spinnaker-release
dependencies:
  consul:
    version: 0.7.5
  redis:
    version: 2:2.8.4-2
  vault:
    version: 0.7.0
service:
  clouddriver:
    commit: 3f4607bc2d181b3c911340c4b380d97a64448996
    version: 5.81.1
  deck:
    commit: 734c895959823eb47e0bef9fe2da072760d97b46
    version: 3.14.1
  echo:
    commit: d20da255de1fabb96d67012c8d9bc370ba02982c
    version: 2.38.1
  fiat:
    commit: c065b481ccd1901d00bf18ed1e29a5678e9c50e2
    version: 1.41.1
  front50:
    commit: f0494570576de5aa33d969fdd7fad93dee2479cc
    version: 2.29.2
  gate:
    commit: 7bc7898bee5c38a200fafbf391126a41dd4aa594
    version: 6.59.2
  igor:
    commit: 3fb141e655c4e4728f561817dbc54a0306c19094
    version: 4.12.1
  kayenta:
    commit: 6c73ba3c0b2892d7cb3f6777ccfc2db92295c463
    version: 2.37.1
  monitoring-daemon:
    commit: 96d510cb22f65dcf788324ed8b68447c31de255a
    version: 1.4.0
  monitoring-third-party:
    commit: 96d510cb22f65dcf788324ed8b68447c31de255a
    version: 1.4.0
  orca:
    commit: c68cc87dd348eb5186411aa46dbf7bcff09ff6ee
    version: 8.33.1
  rosco:
    commit: 4ec52a45986da73e27a8ceba1f3c34f53283d614
    version: 1.17.1
timestamp: "2023-09-08 17:49:41"
version: 1.31.2
```

This is a sample `versions.yml`, as consumed by Halyard:

```yaml
illegalVersions:
- reason: Broken apache config makes the UI unreachable
  version: 1.2.0
- reason: UI does not load
  version: 1.4.0
latestHalyard: 1.62.0
latestSpinnaker: 1.32.2
versions:
- alias: v1.32.2
  changelog: https://spinnaker.io/changelogs/1.32.2-changelog/
  lastUpdate: 1695243355135
  minimumHalyardVersion: '1.45'
  version: 1.32.2
- alias: v1.31.2
  changelog: https://spinnaker.io/changelogs/1.31.2-changelog/
  lastUpdate: 1694195391240
  minimumHalyardVersion: '1.45'
  version: 1.31.2
- alias: v1.30.4
  changelog: https://spinnaker.io/changelogs/1.30.4-changelog/
  lastUpdate: 1694207655470
  minimumHalyardVersion: '1.45'
  version: 1.30.4

```
