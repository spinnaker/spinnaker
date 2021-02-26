# @spinnaker/pluginsdk-peerdeps

This package contains a `package.json` with `peerDependencies`.

A Deck plugin should have a dependency on `@spinnaker/pluginsdk-peerdeps`. The package.json in this directory
has `peerDependencies` and `peerDependenciesMeta`
These fields inform a plugin which versions of packages it should install using `check-peer-dependencies`.

# Updating this package

The source of truth for package verions comes from deck (`../../package.json`) for most packages. This package has a few
script that help keep the `peerDependencies` up to date.

- `yarn sync-versions-from-deck`: Synchronize all peerDependencies with the versions found in ../../package.json
- `yarn interactive`: Interactively upgrade individual `devDependencies`
