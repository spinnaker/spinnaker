#!/usr/bin/env node
/* eslint-disable no-console */

const path = require('path');
const fs = require('fs');

// Rename these files
const fileMapping = {
  'tsconfig.json': 'scaffold.tsconfig.json',
  '.prettierrc.js': 'scaffold.prettierrc.js',
  // 'scaffold-deck.gradle': `${scaffoldTargetDir}.gradle`,
};

const file = require('yargs').argv._[0];
const sourceFile = path.resolve('node_modules', '@spinnaker', 'pluginsdk', 'scaffold', fileMapping[file] || file);
const destFile = path.resolve('.', file);

const bytes = fs.readFileSync(sourceFile);
fs.writeFileSync(destFile, bytes);
