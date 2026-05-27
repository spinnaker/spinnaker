#!/usr/bin/env node

const path = require('path');
const util = require('util');
const exec = util.promisify(require('child_process').exec);

const DECK_ROOT = path.resolve(__dirname, '..');
const PACKAGES_ROOT = path.resolve(`${DECK_ROOT}/packages`);
const KAYENTA_ROOT = path.resolve(DECK_ROOT, '..', 'deck-kayenta');

const runPnpmBuild = (pathToPackage) => {
  const cmd = `pnpm --dir ${pathToPackage} build`;
  console.log(cmd);
  return exec(cmd);
};

async function buildModules() {
  try {
    await runPnpmBuild(`${PACKAGES_ROOT}/presentation`);
    await runPnpmBuild(`${PACKAGES_ROOT}/core`);
    await Promise.all(
      [
        'amazon',
        'appengine',
        'azure',
        'cloudfoundry',
        'cloudrun',
        'docker',
        'google',
        'huaweicloud',
        'kubernetes',
        'oracle',
        'tencentcloud',
      ].map((module) => runPnpmBuild(`${PACKAGES_ROOT}/${module}`)),
    );

    await Promise.all(['ecs', 'titus'].map((module) => runPnpmBuild(`${PACKAGES_ROOT}/${module}`)));
    await runPnpmBuild(KAYENTA_ROOT);
  } catch (err) {
    console.log(err.stdout);
    console.error(err.stderr);
    process.exit(255);
  }
}

buildModules();
