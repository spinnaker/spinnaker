#!/usr/bin/env node
/* @ts-check */
/* eslint-disable no-console */

const path = require('path');
const fs = require('fs');
const { execSync } = require('child_process');
const readlineSync = require('readline-sync');

const yargs = require('yargs')
  .option('directory', {
    type: 'string',
  })
  .option('name', {
    type: 'string',
  }).argv;

const pluginNameQuestion = 'Enter the short name for your plugin (default: myplugin): ';
const pluginName = yargs.name || readlineSync.question(pluginNameQuestion, { defaultInput: 'myplugin' });

const dirQuestion = `Directory to scaffold into (default: ${pluginName}-deck): `;
const scaffoldTargetDir = yargs.directory || readlineSync.question(dirQuestion, { defaultInput: `${pluginName}-deck` });
const basename = path.basename(path.resolve(scaffoldTargetDir));

// Rename these files
const fileMapping = {
  'scaffold.tsconfig.json': 'tsconfig.json',
  'scaffold.prettierrc.js': '.prettierrc.js',
  'scaffold-deck.gradle': `${basename}.gradle`,
};

const renamedFile = (file) => fileMapping[file] || file;

function mkdirIfNotExists(dirPath) {
  if (!fs.existsSync(dirPath)) {
    fs.mkdirSync(dirPath);
  }
}

function recursiveCopy(srcDir, targetDir) {
  const contents = fs.readdirSync(srcDir);
  contents
    .filter((x) => x !== '.' && x !== '..')
    .forEach((file) => {
      const srcPath = path.resolve(srcDir, file);
      const targetPath = path.resolve(targetDir, renamedFile(file));
      const stat = fs.statSync(srcPath);
      if (stat.isDirectory()) {
        mkdirIfNotExists(targetPath);
        recursiveCopy(srcPath, targetPath);
      } else if (stat.isFile()) {
        fs.writeFileSync(targetPath, fs.readFileSync(srcPath));
      }
    });
}

function updatePackageJson(pkgJsonPath, basename) {
  const pluginSdkPackageContents = fs.readFileSync(path.resolve(__dirname, '..', 'package.json')).toString();
  const pluginSdkVersion = JSON.parse(pluginSdkPackageContents).version;
  const pkgJson = JSON.parse(fs.readFileSync(pkgJsonPath).toString());
  pkgJson.name = basename;
  pkgJson.dependencies['@spinnaker/pluginsdk'] = pluginSdkVersion;
  pkgJson.files = ['build/dist'];
  fs.writeFileSync(pkgJsonPath, JSON.stringify(pkgJson, null, 2));
}

mkdirIfNotExists(scaffoldTargetDir);
recursiveCopy(path.resolve(__dirname, '..', 'scaffold'), scaffoldTargetDir);
updatePackageJson(path.resolve(scaffoldTargetDir, 'package.json'), basename);

console.log(`Deck plugin scaffolded into ${scaffoldTargetDir}`);
console.log(`Installing dependencies using 'yarn' and 'npx check-peer-dependencies --install' ...`);

process.chdir(scaffoldTargetDir);
console.log(`yarn`);
execSync(`yarn`, { stdio: 'inherit' });

console.log(`npx check-peer-dependencies --install`);
execSync(`npx check-peer-dependencies --install`, { stdio: 'inherit' });
