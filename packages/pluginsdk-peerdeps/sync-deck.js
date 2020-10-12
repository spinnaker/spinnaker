#!/usr/bin/env node
/* @ts-check */
/* eslint-disable no-console */
/* Synchronizes versions of all (non-dev) dependencies from Deck's root package.json */

const fs = require(`fs`);
const path = require(`path`);

// Deck's package.json
const deckPackagePath = path.resolve(__dirname, '..', '..', 'package.json');
// @spinnaker/core
const corePackagePath = path.resolve(__dirname, '..', '..', 'app', 'scripts', 'modules', 'core', 'package.json');
const corePackageJson = JSON.parse(fs.readFileSync(corePackagePath).toString());
const coreVersion = corePackageJson.version;

// package.json in the current directory
const packageJsonPath = path.resolve('package.json');
const packageJson = JSON.parse(fs.readFileSync(packageJsonPath).toString());
packageJson.dependencies = packageJson.dependencies || {};

const packagesToSync = Object.keys(packageJson.dependencies);
const sourcesOfTruth = [deckPackagePath];

const versionToUse = sourcesOfTruth
  .map((path) => JSON.parse(fs.readFileSync(path).toString()))
  .reduce((packages, pkgJson) => {
    const { peerDependencies = {}, devDependencies = {}, dependencies = {} } = pkgJson;

    return {
      ...packages,
      ...peerDependencies,
      ...devDependencies,
      ...dependencies,
    };
  }, {});

packageJson.dependencies['@spinnaker/core'] = coreVersion;
packagesToSync.forEach((dep) => {
  packageJson.dependencies[dep] = versionToUse[dep] || packageJson.dependencies[dep];
});

fs.writeFileSync(packageJsonPath, JSON.stringify(packageJson, null, 2));

console.log(`Synchronized non-dev peer peerDependencies to ${packageJsonPath} from Deck`);
