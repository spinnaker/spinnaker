#!/usr/bin/env node
// Synchronizes peerDependencies from the versions in dependencies and devDependencies

const path = require('path');
const fs = require('fs');

const pluginSdkPackageJson = JSON.parse(fs.readFileSync(path.resolve(__dirname, '..', 'package.json')).toString());

const srcDeps = Object.entries({
  ...(pluginSdkPackageJson.dependencies || {}),
  ...(pluginSdkPackageJson.devDependencies || {}),
});

const targetDeps = Object.entries(pluginSdkPackageJson.peerDependencies);

console.log('Synchronizing peerDependencies versions from dependencies and devDependencies');
const targetDepsToBump = srcDeps.filter(([srcDep, srcVer]) => {
  // Find matching target dependency and version
  const [targetDep, targetVer] = targetDeps.find(([targetDep, _targetVer]) => targetDep === srcDep) || [];
  return !!targetDep && targetVer !== srcVer;
});

const isDev = ([pkg]) => {
  console.log(`isDev ${pkg}: ${!!pluginSdkPackageJson.devDependencies[pkg]}`);
  return !!pluginSdkPackageJson.devDependencies[pkg];
};

const packagesAffected = targetDepsToBump.map(([dep]) => dep).join(' ');
const bumps = targetDepsToBump
  .filter(x => !isDev(x))
  .map(([dep, version]) => `${dep}@${version}`)
  .join(' ');
const devBumps = targetDepsToBump
  .filter(x => isDev(x))
  .map(([dep, version]) => `${dep}@${version}`)
  .join(' ');

if (packagesAffected.length) {
  console.log();
  console.log(`yarn remove ${packagesAffected}`);
  console.log(`yarn add --peer ${bumps} ${devBumps}`);
  bumps.length && console.log(`yarn add ${bumps} --exact`);
  devBumps.length && console.log(`yarn add --dev ${devBumps} --exact`);
}
