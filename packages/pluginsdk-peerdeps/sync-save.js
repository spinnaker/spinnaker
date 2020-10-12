#!/usr/bin/env node
/* @ts-check */
/* eslint-disable no-console */
/*
This script synchronizes the contents of the scaffolded plugin's package.json and re-writes the
peerDependencies and devPeerDependencies of the @spinnaker/pluginsdk-peerdeps package.
 */

const fs = require(`fs`);
const path = require(`path`);
// This package's dir
const packageDir = path.resolve(__dirname);

if (!fs.existsSync(`.scaffolddir`)) {
  console.error(`Scaffold directory unknown (no .scaffolddir file was found)`);
  console.error();
  console.error(`run "yarn sync-prep" to re-scaffold`);
}

//  The scaffolded plugin's temp dir
const scaffoldDir = fs.readFileSync(`.scaffolddir`).toString().trim();

if (!fs.existsSync(scaffoldDir)) {
  console.error(`Scaffold directory not found at ${scaffoldDir}`);
  console.error();
  console.error(`run "yarn sync-prep" to re-scaffold`);
}

const sourcePackageJson = JSON.parse(fs.readFileSync(path.resolve(scaffoldDir, 'package.json')).toString());
const packageJsonPath = path.resolve(packageDir, 'package.json');
const destJson = JSON.parse(fs.readFileSync(packageJsonPath).toString());

const deps = Object.keys(sourcePackageJson.dependencies).sort();
const devDeps = Object.keys(sourcePackageJson.devDependencies).sort();

destJson.peerDependencies = {};
destJson['peerDevDependencies.doc'] = 'These will be installed as devDependencies by check-peer-dependencies --install';
destJson.peerDevDependencies = devDeps;

deps.forEach((dep) => (destJson.peerDependencies[dep] = sourcePackageJson.dependencies[dep]));
devDeps.forEach((dep) => (destJson.peerDependencies[dep] = sourcePackageJson.devDependencies[dep]));

fs.writeFileSync(packageJsonPath, JSON.stringify(destJson, null, 2));

console.log(`Wrote updated peerDependencies to ${packageJsonPath}`);
