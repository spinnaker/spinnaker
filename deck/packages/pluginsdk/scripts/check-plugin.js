#!/usr/bin/env node

/* eslint-disable no-console */
const yargs = require('yargs');
const { execSync } = require('child_process');
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

  /**
   * @param message { string }
   * @param ok { boolean }
   * @param resolution {{
   *   description: string
   *   command?: string
   *   fixer?: Function
   * }}
   */
  function reporter(message, ok, resolution = {}) {
    const fixer = resolution.fixer || (resolution.command && (() => execSync(resolution.command)));

    if (ok === true) {
      if (verbose) {
        console.log(`  ✅  ${message}`);
      }
    } else {
      if (ok === false) {
        console.log(`  ❌  Error: ${message}`);
      } else {
        console.log(`  ☑️   Warning: ${message}`);
      }
      if (fixer) {
        errorFixers.push(fixer);
        if (resolution.description) {
          console.log(`      Fix: ${resolution.description}`);
        }
        if (resolution.command) {
          console.log('      Command:');
          console.log();
          console.log(`        ${resolution.command}`);
          console.log();
        }
      }
    }
  }

  linters.forEach((linter) => linter(reporter));

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
    console.log(`Run this command to auto-fix all errors and warnings:`);
    console.log();
    console.log(`npx check-plugin --fix-warnings`);
  } else if (errorFixers.length) {
    console.log(`Run this command to auto-fix all errors:`);
    console.log();
    console.log(`npx check-plugin --fix`);
  }

  console.log();
  console.log();

  if (fix) {
    errorFixers.forEach((fix) => fix());
  }

  if (fixWarnings) {
    warningFixers.forEach((fix) => fix());
  }
}
