#!/usr/bin/env node
/* @ts-check */
/* eslint-disable no-console */

/* Copies dependency versions from Deck's root package.json (../../package.json) */

const fs = require(`fs`);
const path = require(`path`);
const yargs = require('yargs')
  .usage(`$0 [--no-dev] [--no-peer] [package.json]`)
  .option('source', {
    description: 'The source package.json',
    default: '../../package.json',
  })
  .option('dest', {
    description: 'The destination package.json',
    default: './package.json',
  })
  .option('no-dev', { type: 'boolean', description: 'do not sync devDependencies', default: false })
  .option('no-peer', { type: 'boolean', description: 'do not sync peerDependencies', default: false });

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
      return parse(getPath('../../app/scripts/modules/core/package.json')).version;
    case '@spinnaker/eslint':
      return parse(getPath('../eslint-plugin/package.json')).version;
    default:
      return versionsFromDeck[pkgName];
  }
};

const packageJson = parse(targetPackageJson);
const shouldSkip = (key) =>
  (key === 'devDependencies' && argv['no-dev']) || (key === 'peerDependencies' && argv['no-peer']);

const keys = ['dependencies', 'peerDependencies', 'devDependencies'].filter((key) => !shouldSkip);

keys.forEach((key) => {
  Object.keys(packageJson[key] || {}).forEach((pkgName) => {
    packageJson[key][pkgName] = getDesiredVersion(pkgName) || packageJson[key][pkgName];
  });
});

fs.writeFileSync(
  targetPackageJson,
  JSON.stringify(packageJson, null, 2).replace(/{\s*"dev": true\s*}/g, `{ "dev": true }`),
);

console.log(`Synchronized dependencies in ${targetPackageJson} from Deck`);
