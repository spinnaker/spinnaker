require('ts-node/register');

const fs = require('fs');
const path = require('path');
const process = require('process');
const minimist = require('minimist');
const { MountebankService } = require('./test/functional/tools/MountebankService');
const { FixtureService } = require('./test/functional/tools/FixtureService');

const flags = minimist(process.argv.slice(2), {
  default: {
    'replay-fixtures': false,
    'record-fixtures': false,
    'mountebank-port': 2525,
    'gate-port': 18084,
    'imposter-port': 8084,
    browser: 'chrome',
    headless: false,
    savelogs: false,
  },
});

if (flags.savelogs && flags.browser !== 'chrome') {
  console.warn('fetching browser logs is only supported by chrome driver; disabling --savelogs');
  flags.savelogs = false;
}

const mountebankService = MountebankService.builder()
  .mountebankPath(path.resolve(__dirname, './node_modules/.bin/mb'))
  .mountebankPort(flags['mountebank-port'])
  .gatePort(flags['gate-port'])
  .imposterPort(flags['imposter-port'])
  .build();

let testRun = null;

const config = {
  specs: ['test/functional/tests/**/*.spec.ts'],
  runner: 'local',
  path: '/wd/hub',
  maxInstances: 1,
  capabilities: [
    {
      maxInstances: 1,
      browserName: flags.browser,
    },
  ],
  sync: true,
  logLevel: 'error', // silent | verbose | command | data | result | error
  coloredLogs: false,
  deprecationWarnings: false,
  bail: 0,
  screenshotPath: null,
  baseUrl: 'http://localhost:9000',
  waitforTimeout: 10000,
  connectionRetryTimeout: 90000,
  connectionRetryCount: 3,
  services: ['selenium-standalone'],
  seleniumLogs: './selenium-logs',
  framework: 'jasmine',
  reporters: ['spec'],
  jasmineNodeOpts: {
    defaultTimeoutInterval: 60000,
  },
  suites: {
    google: ['./src/google/**/*.spec.ts'],
  },

  beforeSession: (_config, _capabilities, specs) => {
    if (!specs.length) {
      return;
    }
    if (!flags['replay-fixtures'] && !flags['record-fixtures']) {
      return;
    }
    const fixtureService = new FixtureService();
    testRun = { fixtureFile: fixtureService.fixturePathForTestPath(specs[0]) };
    return mountebankService.removeImposters().then(() => {
      if (flags['record-fixtures']) {
        return mountebankService.beginRecording();
      } else {
        return mountebankService.createImposterFromFixtureFile(
          testRun.fixtureFile,
          fixtureService.anonymousAuthFixturePath(),
        );
      }
    });
  },

  beforeTest: function(test, context) {
    browser.setWindowSize(1280, 1024);
  },

  afterTest: function(test) {
    if (flags['record-fixtures']) {
      if (test.passed) {
        mountebankService
          .saveRecording(testRun.fixtureFile)
          .then(() => {
            console.log(`wrote fixture to ${testRun.fixtureFile}`);
          })
          .catch(err => {
            console.log(`error saving recording: ${err}`);
          });
      } else {
        console.log(`test failed: "${test.fullName}"; network fixture will not be saved.`);
      }
    }
    if (flags.savelogs && browser.sessionId) {
      const outPath = path.resolve(__dirname, './' + browser.sessionId + '.browser.log');
      fs.writeFileSync(outPath, JSON.stringify(browser.log('browser'), null, 4));
      console.log(`browser log written to ${outPath}`);
    }
  },
};

if (flags.headless) {
  config.capabilities.forEach(cap => {
    if (cap.browserName === 'chrome') {
      cap['goog:chromeOptions'] = cap['goog:chromeOptions'] || {};
      cap['goog:chromeOptions'].args = cap['goog:chromeOptions'].args || [];
      cap['goog:chromeOptions'].args.push('--headless');
    } else if (cap.browserName === 'firefox') {
      cap['moz:firefoxOptions'] = cap['moz:firefoxOptions'] || {};
      cap['moz:firefoxOptions'].args = cap['moz:firefoxOptions'].args || [];
      cap['moz:firefoxOptions'].args.push('-headless');
    }
  });
}

exports.config = config;
