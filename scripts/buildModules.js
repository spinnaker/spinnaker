#!/usr/bin/env node

const path = require('path');
const util = require('util');
const exec = util.promisify(require('child_process').exec);

const DECK_ROOT = path.resolve(__dirname, '..');

const runYarnBuild = (module) => {
  const pathToModule = path.resolve(`${DECK_ROOT}/app/scripts/modules/${module}`);
  const cmd = `yarn --cwd ${pathToModule} build`;
  console.log(cmd);
  return exec(cmd);
};

runYarnBuild('core')
  .then(() =>
    Promise.all(
      [
        'amazon',
        'appengine',
        'azure',
        'cloudfoundry',
        'docker',
        'google',
        'huaweicloud',
        'kubernetes',
        'oracle',
        'tencentcloud',
      ].map(runYarnBuild),
    ),
  )
  .then(() => Promise.all(['ecs', 'titus'].map(runYarnBuild)));
