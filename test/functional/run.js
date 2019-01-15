#!/usr/bin/env node

require('ts-node/register');

const path = require('path');
const process = require('process');
const minimist = require('minimist');
const { TestRunner } = require('./tools/TestRunner');

const repoRoot = path.resolve(__dirname, '../../');
const testRunner = new TestRunner(repoRoot);

process.on('SIGINT', exitCode => {
  console.log('received SIGINT, exiting');
  testRunner.cleanUp();
  process.exit(exitCode);
});

process.on('uncaughtException', err => {
  console.log(err.message + '\n' + err.stack);
  testRunner.cleanUp();
  process.exit(255);
});

const flagDefaults = {
  'serve-deck': false,
  'replay-fixtures': false,
  'record-fixtures': false,
  'gate-port': 18084,
  'imposter-port': 8084,
  browser: 'chrome',
  headless: false,
  savelogs: false,
  help: false,
};

function main() {
  const flags = minimist(process.argv.slice(2), {
    default: flagDefaults,
    '--': true,
  });

  if (flags.help) {
    printUsage();
  }

  if (flags.savelogs && flags.browser !== 'chrome') {
    throw new Error('fetching browser logs is only supported by chrome driver; use --browser chrome');
  }

  if (flags['--'].includes('--help')) {
    // print webdriver.io's help text
    testRunner.run(flags);
    return;
  }

  Object.keys(flags).forEach(f => {
    if (f === '--' || f === '_') {
      return;
    }
    if (!flagDefaults.hasOwnProperty(f)) {
      console.log(`unrecognized flag --${f}`);
      printUsage(1);
    }
  });

  if (flags['replay-fixtures'] || flags['record-fixtures']) {
    testRunner.launchMockServer();
  }

  if (flags['serve-deck']) {
    testRunner.launchStaticServer();
  }

  testRunner.run(flags);
}

function printUsage(errorCode = 0) {
  console.log(
    `
Usage: test/functional/run.js [options] -- [webdriver.io options]

Run functional tests against Deck. Spins up an imposter server to play back
network responses so you don't need to deploy Spinnaker and lots of
infrastructure to exercise Deck.

Any arguments sent after '--' will be passed to webdriver.io's test runner,
which actually executes the tests. Webdriver.io's flags can be viewed at
http://webdriver.io/guide/testrunner/gettingstarted.html

See test/functional/README.md for further information.

Options:
--serve-deck           Boolean. Build and test the production version of Deck.
--replay-fixtures      Boolean. Use pre-recorded network fixtures in place of a
                       running Gate server.
--record-fixtures      Boolean. Record network fixtures for later replay.
                       Requires a running Spinnaker deployment.
--gate-port            (only used when recording fixtures) Port on which a
                       real Gate server is currently running. The imposter
                       recording a test's network fixture will proxy requests
                       from Deck to this port and record the results.
--imposter-port        (only used when recording or replaying fixtures)
                       Port on which the imposter Gate server should be created.
                       This should typically be the port that Gate normally
                       runs on (8084).
--browser              Either "chrome" or "firefox".
--headless             Boolean. Run the browser in headless mode.
--savelogs             Chrome-only. Save browser's console log to file named
                       [selenium-session-id].browser.log.
--help                 Print the script's help information.

Example usage:

# Run tests in headless Chrome with a prebuilt Deck and without
# Gate. Save browser logs when tests complete.
test/functional/run.js --serve-deck --replay-fixtures --headless --savelogs
`.trim(),
  );
  process.exit(errorCode);
}

main();
