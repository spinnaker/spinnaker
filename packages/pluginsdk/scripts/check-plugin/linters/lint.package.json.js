const { execSync } = require('child_process');
const { assertJsonFile } = require('../asserters/assertJsonFile');
const { readJson, writeJson } = require('../util/readWriteJson');

function getLatestSdkVersion() {
  const versionsString = execSync('npm info @spinnaker/pluginsdk versions').toString();
  return JSON.parse(versionsString.replace(/'/g, '"')).pop();
}

function checkPackageJson(report) {
  const pkgJson = readJson('package.json');
  const deps = pkgJson.dependencies || {};
  const sdk = deps['@spinnaker/pluginsdk'];
  const latest = getLatestSdkVersion();

  const latestVersionFixer = () => {
    execSync(`yarn add @spinnaker/pluginsdk@${latest}`, { stdio: 'inherit' });
    console.log(`fixed: installed @spinnaker/pluginsdk@${latest}`);
  };
  const latestVersionResolution = '--fix: install latest @spinnaker/pluginsdk';
  report('Uses latest @spinnaker/pluginsdk', sdk === latest, latestVersionResolution, latestVersionFixer);

  const checkPackageJsonField = assertJsonFile(report, 'package.json', pkgJson);

  checkPackageJsonField('module', 'build/dist/index.js');
  checkPackageJsonField('scripts.clean', 'npx shx rm -rf build');
  checkPackageJsonField('scripts.build', 'rollup -c');
  checkPackageJsonField('scripts.watch', 'rollup -c -w');
  checkPackageJsonField('scripts.postinstall', 'check-plugin && check-peer-dependencies || true');

  const bundlesFiles = pkgJson.files && pkgJson.files.includes('build/dist');
  const bundlesFilesFixer = () => {
    const json = readJson('package.json');
    if (!json.files) {
      json.files = [];
    }
    json.files.push('build/dist');
    writeJson('package.json', json);

    console.log(`fixed: added "build/dist" to "files" in package.json`);
  };
  const resolution = `--fix: Add "build/dist" to files array in package.json`;
  report('package.json: files includes "build/dist"', bundlesFiles, resolution, bundlesFilesFixer);
}

module.exports = { checkPackageJson };
