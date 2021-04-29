/* eslint-disable no-console */

const { execSync } = require('child_process');
const { assertJsonFile } = require('../asserters/assertJsonFile');
const { readJson } = require('../util/readWriteJson');

const PLUGIN_SDK = '@spinnaker/pluginsdk';
const PEER_DEPS = '@spinnaker/pluginsdk-peerdeps';

function getLatestPackageVersion(pkg) {
  const versionsString = execSync(`npm info ${pkg} versions`).toString();
  return JSON.parse(versionsString.replace(/'/g, '"')).pop();
}

function getInstalledPackageVersion(pkgJson, pkg) {
  return (
    (pkgJson.dependencies && pkgJson.dependencies[pkg]) || (pkgJson.devDependencies && pkgJson.devDependencies[pkg])
  );
}

function checkPackageJson(report) {
  const pkgJson = readJson('package.json');

  const latestSdkVersion = getLatestPackageVersion(PLUGIN_SDK);
  const installedSdkVersion = getInstalledPackageVersion(pkgJson, PLUGIN_SDK);

  report(
    installedSdkVersion
      ? `This plugin uses an out of date ${PLUGIN_SDK}@${installedSdkVersion}`
      : `This plugin does not have ${PLUGIN_SDK} installed`,
    installedSdkVersion === latestSdkVersion,
    {
      description: `Install ${PLUGIN_SDK}@${latestSdkVersion}`,
      command: `yarn add ${PLUGIN_SDK}@${latestSdkVersion}`,
    },
  );

  const latestPeerDepsVersion = getLatestPackageVersion(PEER_DEPS);
  const installedPeerDepsVersion = getInstalledPackageVersion(pkgJson, PEER_DEPS);

  report(
    installedPeerDepsVersion
      ? `This plugin uses an out of date ${PEER_DEPS}@${installedPeerDepsVersion}`
      : `This plugin does not have ${PEER_DEPS} installed`,
    installedPeerDepsVersion === latestPeerDepsVersion,
    {
      description: `Install ${PEER_DEPS}@${latestPeerDepsVersion}`,
      command: `yarn add ${PEER_DEPS}@${latestPeerDepsVersion}`,
    },
  );

  const checkPackageJsonField = assertJsonFile(report, 'package.json', pkgJson);

  checkPackageJsonField('devDependencies.husky', undefined);
  checkPackageJsonField('dependencies.husky', undefined);
  checkPackageJsonField('scripts.build', 'npm run clean && NODE_ENV=production rollup -c');
  checkPackageJsonField('scripts.clean', 'npx shx rm -rf build');
  checkPackageJsonField('scripts.lint', 'eslint --ext js,jsx,ts,tsx src');
  checkPackageJsonField('scripts.develop', 'npm run clean && run-p watch proxy');
  checkPackageJsonField('scripts.postinstall', 'check-plugin && check-peer-dependencies || true');
  checkPackageJsonField('scripts.prepare', 'husky-install');
  checkPackageJsonField('scripts.prettier', "prettier --write 'src/**/*.{js,jsx,ts,tsx,html,css,less,json}'");
  checkPackageJsonField('scripts.proxy', 'dev-proxy');
  checkPackageJsonField('scripts.watch', 'rollup -c -w --no-watch.clearScreen');
}

module.exports = { checkPackageJson };
