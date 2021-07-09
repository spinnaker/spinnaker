#!/usr/bin/env node
/* @ts-check */
/* eslint-disable no-console */

/* Copies dependency versions from Deck's root package.json (../../package.json) */

const fs = require(`fs`);
const path = require(`path`);
const yargs = require('yargs')
  .usage(`$0 [--no-dev] [--no-peer] [--no-spinnaker] [--source package.json] [--dest otherpackage.json]`)
  .option('source', {
    description: 'The source package.json',
    default: '../../package.json',
  })
  .option('dest', {
    description: 'The destination package.json',
    default: './package.json',
  })
  .option('spinnaker', { type: 'boolean', description: 'include @spinnaker/* dependencies', default: true })
  .option('dev', { type: 'boolean', description: 'include devDependencies', default: true })
  .option('peer', { type: 'boolean', description: 'include peerDependencies', default: true });

const { argv } = yargs;
const targetPackageJson = path.resolve(argv.dest);

const getPath = (string) => path.resolve(__dirname, ...string.split('/'));
const parse = (path) => JSON.parse(fs.readFileSync(path).toString());

const sourcePackageJson = parse(getPath(argv.source));
// { [package]: version } from deck's package.json
const versionsFromDeck = {
  ...sourcePackageJson.peerDependencies,
  ...sourcePackageJson.devDependencies,
  ...sourcePackageJson.dependencies,
};

const getDesiredVersion = (pkgName) => {
  switch (pkgName) {
    case '@spinnaker/core':
      return parse(getPath('../core/package.json')).version;
    case '@spinnaker/eslint-plugin':
      return parse(getPath('../eslint-plugin/package.json')).version;
    case '@spinnaker/presentation':
      return parse(getPath('../presentation/package.json')).version;
    default:
      return versionsFromDeck[pkgName];
  }
};

const shouldSkipType = (key) =>
  (key === 'devDependencies' && argv.dev === false) || (key === 'peerDependencies' && argv.peer === false);
const shouldSkipPackage = (packageName) => argv.spinnaker === false && packageName.startsWith('@spinnaker/');

const packageJson = parse(targetPackageJson);
const keys = ['dependencies', 'peerDependencies', 'devDependencies'].filter((key) => !shouldSkipType(key));
keys.forEach((key) => {
  Object.keys(packageJson[key] || {}).forEach((pkgName) => {
    if (!shouldSkipPackage(pkgName)) {
      packageJson[key][pkgName] = getDesiredVersion(pkgName) || packageJson[key][pkgName];
    }
  });
});

fs.writeFileSync(
  targetPackageJson,
  JSON.stringify(packageJson, null, 2).replace(/{\s*"dev": true\s*}/g, `{ "dev": true }`),
);

console.log(`Synchronized dependencies in ${targetPackageJson} from Deck`);
