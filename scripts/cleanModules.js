#!/usr/bin/env node

const path = require('path');
const util = require('util');
const exec = util.promisify(require('child_process').exec);

const DECK_ROOT = path.resolve(__dirname, '..');
const modules = [
  'amazon',
  'app',
  'appengine',
  'azure',
  'cloudfoundry',
  'core',
  'docker',
  'ecs',
  'google',
  'huaweicloud',
  'kubernetes',
  'oracle',
  'tencentcloud',
  'titus',
];

Promise.all(
  modules.map((md) => {
    const pathToModule = path.resolve(`${DECK_ROOT}/app/scripts/modules/${md}`);
    console.log(`Cleaning ${md}`);
    return exec(`cd ${pathToModule} && rm -rf node_modules dist`);
  }),
).then(() => {
  console.log('Running yarn');
  return exec(`cd ${DECK_ROOT} && yarn`);
});
