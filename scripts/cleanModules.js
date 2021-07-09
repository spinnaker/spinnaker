#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const util = require('util');
const exec = util.promisify(require('child_process').exec);

const DECK_ROOT = path.resolve(__dirname, '..');
const readDirsFromPath = (path) =>
  fs
    .readdirSync(path, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => `${path}/${entry.name}`);

const ignorePackages = new Set(['mocks', 'pluginsdk-peerdeps', 'dcos']);
const packages = readDirsFromPath(path.resolve(`${DECK_ROOT}/packages`)).filter(
  (dirName) => !ignorePackages.has(path.basename(dirName)),
);

Promise.all(
  packages.map((pathToPackage) => {
    console.log(`Cleaning ${pathToPackage}`);
    return exec(`cd ${pathToPackage} && rm -rf node_modules dist`);
  }),
).then(() => {
  console.log('Running yarn');
  return exec(`cd ${DECK_ROOT} && yarn`);
});
