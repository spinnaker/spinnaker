#!/usr/bin/env node

const chalk = require('chalk');
const child_process = require('child_process');
const fs = require('fs');
const loadConfigFile = require('rollup/dist/loadConfigFile');
const ora = require('ora');
const path = require('path');
const process = require('process');
const rollup = require('rollup');

const getRollupConfigPath = (file) => {
  if (file && !fs.existsSync(path.resolve(path.join('.', file)))) {
    // If the user explicitly provides a rollup config file and if it is not available, then throw an error and exit.
    console.error(`Could not find ${path.join('.', file)}`);
    process.exit(1);
  }
  // If user does't provide a rollup config file, then search for `rollup.config.js` in the project directory. If it is
  // not found, then use `rollup.config.base.module.js` which contains pretty reasonable defaults.
  const projectRollupConfigFilePath = path.resolve(path.join('.', file || 'rollup.config.js'));
  return fs.existsSync(projectRollupConfigFilePath)
    ? projectRollupConfigFilePath
    : path.resolve(path.join(__dirname), 'config', 'rollup.config.base.module.js');
};

const OUTPUT_DIR_REGEX = /\/?(\w+)$/;

const getOutputDir = (output) => {
  const outputDirMatch = OUTPUT_DIR_REGEX.exec(output);
  return outputDirMatch.length > 1 ? outputDirMatch[1] : output;
};

const startHandler = async ({ file, push }) => {
  process.env.ROLLUP_WATCH = true;

  const { options, warnings } = await loadConfigFile(getRollupConfigPath(file));
  // A map of `input-output` bundle key to a spinner object. This is used to quickly access a spinner object when
  // succeeding/failing that object on receiving a `BUNDLE_END`/`ERROR` event.
  const spinners = {};
  // Kick-starting rollup's bundling process in watch mode.
  const watcher = rollup.watch(options);

  warnings.flush();
  watcher.on('event', (event) => {
    // Rollup will be emitting lifecycle events for the bundling process such as start, end and error. These events are
    // used to control a spinner in the terminal for each `input-output` bundle.
    switch (event.code) {
      case 'END':
        if (push) {
          console.log();
          console.log(chalk.gray.bold('yalc push'));
          const yalcBin = path.resolve(__dirname, 'node_modules', '.bin', 'yalc');
          const output = child_process.execSync(`${yalcBin} push`);
          console.log(chalk.blue.bold(output.toString('utf-8')));
        }
        break;
      case 'BUNDLE_START':
        console.log('');
        event.output.forEach((output) => {
          const outputDir = getOutputDir(output);
          const spinner = ora({
            text: chalk.blue.bold(`${event.input} -> ${outputDir}`),
          });
          spinners[`${event.input}-${output}`] = spinner;

          spinner.start();
        });
        break;
      case 'BUNDLE_END':
        event.output.forEach((output) => {
          const spinner = spinners[`${event.input}-${output}`];
          if (spinner) {
            const outputDir = getOutputDir(output);
            spinner.succeed(chalk.green.bold(`${event.input} -> ${outputDir}`));
            delete spinners[`${event.input}-${output}`];
          }
        });
        break;
      case 'ERROR':
        Object.entries(spinners).forEach(([key, spinner]) => {
          spinner.fail(event.error);
          console.error(event.error);
          delete spinners[key];
        });
        break;
    }
  });

  watcher.close();
};

const printBundleStart = (option) => {
  let message = option.output.map((output) => `${option.input} -> ${output.dir}...`).join('\n');
  console.log(chalk.blue.bold(message));
};
const printBundleComplete = (option, completedTimeInMS) => {
  const outputFolder = chalk.green.bold(option.output.map((output) => output.dir).join(', '));
  const completedTimeInS = chalk.green.bold(`${completedTimeInMS / 1000}s`);
  console.log(chalk.green(`created ${outputFolder} in ${completedTimeInS}`));
};

const buildHandler = async ({ file }) => {
  const { options, warnings } = await loadConfigFile(getRollupConfigPath(file));
  warnings.flush();

  for (const o of options) {
    const start = Date.now();
    console.log('');
    printBundleStart(o);

    const bundle = await rollup.rollup(o);
    await Promise.all(o.output.map(bundle.write));

    printBundleComplete(o, Date.now() - start);
  }
};

const fileOption = {
  alias: 'file',
  describe: 'custom rollup config file',
  type: 'string',
};

const pushOption = {
  alias: 'push',
  default: false,
  type: 'boolean',
};

require('yargs')
  .scriptName('spinnaker-scripts')
  .command('start', 'Builds your package in watch mode', { f: fileOption, p: pushOption }, startHandler)
  .command('build', 'Builds your package', { f: fileOption }, buildHandler)
  .help()
  .demandCommand().argv;
