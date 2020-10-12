/* eslint-disable no-console */

const { execSync } = require('child_process');
const { assertJsonFile } = require('../asserters/assertJsonFile');
const { readJson, writeJson } = require('../util/readWriteJson');
const path = require('path');

function getLatestSdkVersion() {
  const versionsString = execSync('npm info @spinnaker/pluginsdk versions').toString();
  return JSON.parse(versionsString.replace(/'/g, '"')).pop();
}

function checkPackageJson(report) {
  const pkgJson = readJson('package.json');
  const latest = getLatestSdkVersion();
  const sdkPackagePath = path.resolve(__filename, '..', '..', '..', '..', 'package.json');
  const sdkPackage = readJson(sdkPackagePath);
  const sdk = sdkPackage.version;

  report(`This plugin uses an out of date @spinnaker/pluginsdk@${sdk}`, sdk === latest, {
    description: `Install @spinnaker/pluginsdk@${latest}`,
    command: `yarn add @spinnaker/pluginsdk@${latest}`,
  });

  const checkPackageJsonField = assertJsonFile(report, 'package.json', pkgJson);

  checkPackageJsonField('scripts.build', 'npm run clean && rollup -c');
  checkPackageJsonField('scripts.clean', 'npx shx rm -rf build');
  checkPackageJsonField('scripts.develop', 'npm run clean && run-p watch proxy');
  checkPackageJsonField('scripts.postinstall', 'check-plugin && check-peer-dependencies || true');
  checkPackageJsonField('scripts.proxy', 'dev-proxy');
  checkPackageJsonField('scripts.watch', 'rollup -c -w --no-watch.clearScreen');
}

module.exports = { checkPackageJson };
