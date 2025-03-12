#!/usr/bin/env node

/**
 * A temporary utility script that shall be used to bump packages to the latest version across all spinnaker packages
 * until lerna migration is complete.
 */

/* eslint-disable no-console */
const { execSync } = require('child_process');
const { readFileSync } = require('fs');
const path = require('path');

if (process.argv.length < 3) {
  console.log('Error: package name must be provided like `./bumpPackage.js package` ');
  process.exit(1);
}

const packageToUpgrade = process.argv[2];

const packages = [
  'packages/amazon/',
  'packages/appengine/',
  'packages/azure/',
  'packages/cloudfoundry/',
  'packages/cloudrun/',
  'packages/core/',
  'packages/docker/',
  'packages/ecs/',
  'packages/google/',
  'packages/huaweicloud/',
  'packages/kubernetes/',
  'packages/oracle/',
  'packages/tencentcloud/',
  'packages/titus/',
  'packages/eslint-plugin/',
  'packages/mocks/',
  'packages/pluginsdk/',
  'packages/pluginsdk/scaffold/',
  'packages/pluginsdk-peerdeps/',
  'packages/presentation/',
  'packages/scripts/',
  'test/functional/',
];

packages
  .map((packagePath) => path.resolve(`${__dirname}/../${packagePath}`))
  .filter((packagePath) => {
    const packageJSON = JSON.parse(readFileSync(path.resolve(packagePath, 'package.json'), 'utf8'));

    return (
      packageToUpgrade in (packageJSON.dependencies || {}) || packageToUpgrade in (packageJSON.devDependencies || {})
    );
  })
  .forEach((package) => {
    console.log(`Handling ${package}`);
    console.log();

    const yarnUpgradeCmd = `yarn --cwd ${package} upgrade --latest ${packageToUpgrade}`;
    console.log(yarnUpgradeCmd);
    console.log();
    console.log(execSync(yarnUpgradeCmd).toString());
    console.log('===============================');
    console.log();
  });
