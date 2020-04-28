#!/usr/bin/env node
const yargs = require('yargs');
const { linters } = require('./check-plugin/linters');

yargs
  .scriptName('check-plugin')
  .option('verbose', {
    alias: 'v',
    type: 'boolean',
    default: false,
  })
  .option('fix', {
    type: 'boolean',
    alias: 'f',
    describe: 'When enabled, fixes are automatically applied',
    default: false,
  })
  .option('fix-warnings', {
    type: 'boolean',
    alias: 'w',
    describe: 'When enabled, fixes are automatically applied even for warnings',
    default: false,
  });

const { verbose, fix, fixWarnings } = yargs.argv;
checkPlugin({ verbose, fixWarnings, fix: fix || fixWarnings });

function checkPlugin(options) {
  const { verbose, fix, fixWarnings } = options;

  const errorFixers = [];
  const warningFixers = [];

  function reporter(message, ok, resolution, fixer) {
    if (ok === true) {
      if (verbose) {
        console.log(`  ✅  ${message}`);
      }
    } else if (ok === false) {
      console.log(`  ❌  ${message}`);
      if (fixer) {
        errorFixers.push(fixer);
        if (resolution) {
          console.log();
          console.log('      ' + resolution);
          console.log();
        }
      }
    } else {
      console.log(`  ☑️   ${message}`);

      if (fixer) {
        warningFixers.push(fixer);
        if (resolution) {
          console.log();
          console.log('      ' + resolution);
          console.log();
        }
      }
    }
  }

  linters.forEach(linter => linter(reporter));

  const fixingErrors = fix && errorFixers.length;
  const fixingWarnings = fixWarnings && warningFixers.length;

  console.log();
  console.log();
  if (fixingErrors || fixingWarnings) {
    if (fixingWarnings) {
      console.log('Fixing errors and warnings...');
    } else {
      console.log('Fixing errors...');
    }
  } else if (warningFixers.length) {
    console.log(`Run this command to fix the errors and warnings:`);
    console.log();
    console.log(`npx check-plugin --fix-warnings`);
  } else if (errorFixers.length) {
    console.log(`Run this command to fix the errors:`);
    console.log();
    console.log(`npx check-plugin --fix`);
  }

  console.log();
  console.log();

  if (fix) {
    errorFixers.forEach(fix => fix());
  }

  if (fixWarnings) {
    warningFixers.forEach(fix => fix());
  }
}
