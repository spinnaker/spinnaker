const { execSync } = require('child_process');
const fs = require('node:fs');
const semver = require('semver');
const { assertJsonFile } = require('../asserters/assertJsonFile');

const PLUGIN_SDK = '@spinnaker/pluginsdk';
const PEER_DEPS = '@spinnaker/pluginsdk-peerdeps';
const SCRIPTS = '@spinnaker/scripts';

function getLatestPackageVersion(pkg) {
  const versionsString = execSync(`npm info ${pkg} versions`).toString();
  return JSON.parse(versionsString.replace(/'/g, '"')).pop();
}

function getInstalledPackageVersion(pkgJson, pkg) {
  return (
    (pkgJson.dependencies && pkgJson.dependencies[pkg]) || (pkgJson.devDependencies && pkgJson.devDependencies[pkg])
  );
}

function includesLatestPackageVersion(declaredVersion, latestVersion) {
  return declaredVersion === 'latest' || semver.satisfies(latestVersion, declaredVersion);
}

function checkPackageJson(report) {
  const pkgJson = JSON.parse(fs.readFileSync('package.json', 'utf8'));

  const latestSdkVersion = getLatestPackageVersion(PLUGIN_SDK);
  const installedSdkVersion = getInstalledPackageVersion(pkgJson, PLUGIN_SDK);

  report(
    installedSdkVersion
      ? `This plugin uses an out of date ${PLUGIN_SDK}@${installedSdkVersion}`
      : `This plugin does not have ${PLUGIN_SDK} installed`,
    Boolean(installedSdkVersion && includesLatestPackageVersion(installedSdkVersion, latestSdkVersion)),
    {
      description: `Install ${PLUGIN_SDK}@${latestSdkVersion}`,
      command: `pnpm add ${PLUGIN_SDK}@${latestSdkVersion}`,
    },
  );

  const latestPeerDepsVersion = getLatestPackageVersion(PEER_DEPS);
  const installedPeerDepsVersion = getInstalledPackageVersion(pkgJson, PEER_DEPS);

  report(
    installedPeerDepsVersion
      ? `This plugin uses an out of date ${PEER_DEPS}@${installedPeerDepsVersion}`
      : `This plugin does not have ${PEER_DEPS} installed`,
    Boolean(installedPeerDepsVersion && includesLatestPackageVersion(installedPeerDepsVersion, latestPeerDepsVersion)),
    {
      description: `Install ${PEER_DEPS}@${latestPeerDepsVersion}`,
      command: `pnpm add ${PEER_DEPS}@${latestPeerDepsVersion}`,
    },
  );

  const latestScriptsVersion = getLatestPackageVersion(SCRIPTS);
  const installedScriptsVersion = getInstalledPackageVersion(pkgJson, SCRIPTS);

  report(
    installedScriptsVersion
      ? `This plugin uses an out of date ${SCRIPTS}@${installedScriptsVersion}`
      : `This plugin does not have ${SCRIPTS} installed`,
    Boolean(installedScriptsVersion && includesLatestPackageVersion(installedScriptsVersion, latestScriptsVersion)),
    {
      description: `Install ${SCRIPTS}@${latestScriptsVersion}`,
      command: `pnpm add ${SCRIPTS}@${latestScriptsVersion}`,
    },
  );

  const checkPackageJsonField = assertJsonFile(report, 'package.json', pkgJson);

  checkPackageJsonField('devDependencies.husky', undefined);
  checkPackageJsonField('dependencies.husky', undefined);
  checkPackageJsonField('scripts.build', 'NODE_ENV=production spinnaker-scripts build');
  checkPackageJsonField('scripts.clean', 'npx shx rm -rf build');
  checkPackageJsonField('scripts.lint', 'eslint --ext js,jsx,ts,tsx src');
  checkPackageJsonField('scripts.develop', 'npm run clean && run-p watch proxy');
  checkPackageJsonField('scripts.postinstall', 'check-plugin && check-peer-dependencies || true');
  checkPackageJsonField('scripts.prepare', 'husky-install');
  checkPackageJsonField('scripts.prettier', "prettier --write 'src/**/*.{js,jsx,ts,tsx,html,css,less,json}'");
  checkPackageJsonField('scripts.proxy', 'dev-proxy');
  checkPackageJsonField('scripts.watch', 'spinnaker-scripts start');
}

module.exports = { checkPackageJson };
