# @spinnaker/pluginsdk-peerdeps

This package contains a `package.json` with `peerDependencies`.

A Deck plugin should have a dependency on `@spinnaker/pluginsdk-peerdeps`. The package.json in this directory
has `peerDependencies` and `peerDependenciesMeta`
These fields inform a plugin which versions of packages it should install using `check-peer-dependencies`.

# Updating this package

The source of truth for package versions comes from deck (`<project-root>/package.json`) for most packages. This package has a few
scripts that will help keep the `peerDependencies` up to date.

- `yarn sync`: Synchronize all `peerDependencies` with the versions found in `<project-root>/package.json`
- `yarn interactive`: Interactively upgrade individual `devDependencies`
