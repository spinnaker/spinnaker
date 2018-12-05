const fs = require('fs');
const path = require('path');
const process = require('process');
const request = require('request-promise-native');
const { spawn } = require('child_process');
const logMountebank = require('debug')('mountebank');
const express = require('express');

const flags = {
  serveStatic: false,
  staticDir: '',
  replayNetwork: false,
  isRecordMode: false,
  browser: 'chrome',
  captureLogs: false,
};

process.argv.forEach((arg, i) => {
  const nextArg = process.argv[i + 1];
  if (arg === '--serve-static') {
    flags.serveStatic = true;
    flags.staticDir = nextArg;
  }
  if (arg === '--replay-network') {
    flags.replayNetwork = true;
  }
  if (arg === '--record') {
    flags.isRecordMode = true;
  }
  if (arg === '--browser') {
    flags.browser = nextArg;
  }
  if (arg === '--capture-logs') {
    flags.captureLogs = true;
  }
});

if (flags.captureLogs && flags.browser !== 'chrome') {
  console.warn('Capturing logs in browsers other than chrome is not supported. Disabling log capture.');
  flags.captureLogs = false;
}

let mountebankProcess;

const testRuns = [];

exports.config = {
  //
  // ==================
  // Specify Test Files
  // ==================
  // Define which test specs should run. The pattern is relative to the directory
  // from which `wdio` was called. Notice that, if you are calling `wdio` from an
  // NPM script (see https://docs.npmjs.com/cli/run-script) then the current working
  // directory is where your package.json resides, so `wdio` will be called from there.
  //
  specs: ['./src/**/*.spec.ts'],
  // Patterns to exclude.
  exclude: [
    // 'path/to/excluded/files'
  ],
  //
  // ============
  // Capabilities
  // ============
  // Define your capabilities here. WebdriverIO can run multiple capabilities at the same
  // time. Depending on the number of capabilities, WebdriverIO launches several test
  // sessions. Within your capabilities you can overwrite the spec and exclude options in
  // order to group specific specs to a specific capability.
  //
  // First, you can define how many instances should be started at the same time. Let's
  // say you have 3 different capabilities (Chrome, Firefox, and Safari) and you have
  // set maxInstances to 1; wdio will spawn 3 processes. Therefore, if you have 10 spec
  // files and you set maxInstances to 10, all spec files will get tested at the same time
  // and 30 processes will get spawned. The property handles how many capabilities
  // from the same test should run tests.
  //
  maxInstances: 1,
  //
  // If you have trouble getting all important capabilities together, check out the
  // Sauce Labs platform configurator - a great tool to configure your capabilities:
  // https://docs.saucelabs.com/reference/platforms-configurator
  //
  capabilities: [
    {
      // maxInstances can get overwritten per capability. So if you have an in-house Selenium
      // grid with only 5 firefox instances available you can make sure that not more than
      // 5 instances get started at a time.
      maxInstances: 1,
      //
      browserName: flags.browser,
      chromeOptions: {
        args: ['--headless', '--disable-gpu', '--window-size=1280,1024'],
      }
    },
  ],
  //
  // ===================
  // Test Configurations
  // ===================
  // Define all options that are relevant for the WebdriverIO instance here
  //
  // By default WebdriverIO commands are executed in a synchronous way using
  // the wdio-sync package. If you still want to run your tests in an async way
  // e.g. using promises you can set the sync option to false.
  sync: true,
  //
  // Level of logging verbosity: silent | verbose | command | data | result | error
  logLevel: 'error',
  //
  // Enables colors for log output.
  coloredLogs: false,
  //
  // Warns when a deprecated command is used
  deprecationWarnings: false,
  //
  // If you only want to run your tests until a specific amount of tests have failed use
  // bail (default is 0 - don't bail, run all tests).
  bail: 0,
  //
  // Saves a screenshot to a given path if a command fails.
  screenshotPath: null,
  //
  // Set a base URL in order to shorten url command calls. If your `url` parameter starts
  // with `/`, the base url gets prepended, not including the path portion of your baseUrl.
  // If your `url` parameter starts without a scheme or `/` (like `some/path`), the base url
  // gets prepended directly.
  baseUrl: 'http://localhost:9000',
  //
  // Default timeout for all waitFor* commands.
  waitforTimeout: 10000,
  //
  // Default timeout in milliseconds for request
  // if Selenium Grid doesn't send response
  connectionRetryTimeout: 90000,
  //
  // Default request retries count
  connectionRetryCount: 3,
  //
  // Initialize the browser instance with a WebdriverIO plugin. The object should have the
  // plugin name as key and the desired plugin options as properties. Make sure you have
  // the plugin installed before running any tests. The following plugins are currently
  // available:
  // WebdriverCSS: https://github.com/webdriverio/webdrivercss
  // WebdriverRTC: https://github.com/webdriverio/webdriverrtc
  // Browserevent: https://github.com/webdriverio/browserevent
  // plugins: {
  //     webdrivercss: {
  //         screenshotRoot: 'my-shots',
  //         failedComparisonsRoot: 'diffs',
  //         misMatchTolerance: 0.05,
  //         screenWidth: [320,480,640,1024]
  //     },
  //     webdriverrtc: {},
  //     browserevent: {}
  // },
  //
  // Test runner services
  // Services take over a specific job you don't want to take care of. They enhance
  // your test setup with almost no effort. Unlike plugins, they don't add new
  // commands. Instead, they hook themselves up into the test process.
  services: ['selenium-standalone'],
  seleniumLogs: './selenium-logs',
  // Framework you want to run your specs with.
  // The following are supported: Mocha, Jasmine, and Cucumber
  // see also: http://webdriver.io/guide/testrunner/frameworks.html
  //
  // Make sure you have the wdio adapter package for the specific framework installed
  // before running any tests.
  framework: 'jasmine',
  //
  // Test reporter for stdout.
  // The only one supported by default is 'dot'
  // see also: http://webdriver.io/guide/reporters/dot.html
  reporters: ['dot'],

  //
  // Options to be passed to Jasmine.
  jasmineNodeOpts: {
    //
    // Jasmine default timeout
    defaultTimeoutInterval: 60000,
    //
    // The Jasmine framework allows interception of each assertion in order to log the state of the application
    // or website depending on the result. For example, it is pretty handy to take a screenshot every time
    // an assertion fails.
    expectationResultHandler: function(passed, assertion) {
      // do something
    },
  },

  suites: {
    google: ['./src/google/**/*.spec.ts'],
  },

  //
  // =====
  // Hooks
  // =====
  // WebdriverIO provides several hooks you can use to interfere with the test process in order to enhance
  // it and to build services around it. You can either apply a single function or an array of
  // methods to it. If one of them returns with a promise, WebdriverIO will wait until that promise got
  // resolved to continue.
  /**
   * Gets executed once before all workers get launched.
   * @param {Object} config wdio configuration object
   * @param {Array.<Object>} capabilities list of capabilities details
   */
  onPrepare: function(config, capabilities) {
    const promises = [];
    if (flags.serveStatic) {
      const app = express();
      app.use(express.static(flags.staticDir));
      promises.push(new Promise((resolve, reject) => {
        app.on('error', e => {
          console.error('Express error: ' + e);
        });
        app.listen(9000, () => {
          console.log('Serving static pages on port 9000');
          resolve()
        });
      }));
    }
    if (flags.replayNetwork || flags.isRecordMode) {
      mountebankProcess = spawn('./node_modules/.bin/mb');
      mountebankProcess.stdout.on('data', data => {
        logMountebank('stdout: %s', data);
      });
      mountebankProcess.stderr.on('data', data => {
        logMountebank('stderr: %s', data);
      });
      mountebankProcess.on('close', code => {
        console.error('Mountebank process exited with code ' + code);
      });
      promises.push(new Promise((resolve, reject) => setTimeout(resolve, 1000)));
    }
    return Promise.all(promises);
  },
  /**
   * Gets executed just before initialising the webdriver session and test framework. It allows you
   * to manipulate configurations depending on the capability or spec.
   * @param {Object} config wdio configuration object
   * @param {Array.<Object>} capabilities list of capabilities details
   * @param {Array.<String>} specs List of spec file paths that are to be run
   */
  // beforeSession: function (config, capabilities, specs) {
  // },
  /**
   * Gets executed before test execution begins. At this point you can access to all global
   * variables like `browser`. It is the perfect place to define custom commands.
   * @param {Array.<Object>} capabilities list of capabilities details
   * @param {Array.<String>} specs List of spec file paths that are to be run
   */
  before: function(capabilities, specs) {},
  /**
   * Runs before a WebdriverIO command gets executed.
   * @param {String} commandName hook command name
   * @param {Array} args arguments that command would receive
   */
  // beforeCommand: function (commandName, args) {
  // },

  /**
   * Hook that gets executed before the suite starts
   * @param {Object} suite suite details
   */
  // beforeSuite: function (suite) {
  // },
  /**
   * Function to be executed before a test (in Mocha/Jasmine) or a step (in Cucumber) starts.
   * @param {Object} test test details
   */
  beforeTest: function(test) {
    browser.windowHandleSize({ width: 1280, height: 1024 });
    const run = { file: test.file };
    testRuns.push(run);
    if (!flags.replayNetwork && !flags.isRecordMode) {
      return;
    }
    run.fixtureFile = test.file + '.mountebank_fixture.json';
    return request('http://localhost:2525/config').then(response => {
      return request.delete('http://localhost:2525/imposters').then(() => {
        if (flags.isRecordMode) {
          return request.post({
            method: 'post',
            json: true,
            uri: 'http://localhost:2525/imposters',
            body: {
              port: 8084,
              protocol: 'http',
              stubs: [
                {
                  responses: [
                    {
                      proxy: {
                        to: 'http://localhost:18084',
                        predicateGenerators: [
                          {
                            matches: { method: true, path: true, query: true },
                            caseSensitive: true,
                          },
                        ],
                      },
                    },
                  ],
                },
              ],
            },
          });
        } else {
          try {
            const rawFixture = fs.readFileSync(run.fixtureFile, { encoding: 'utf8' });
            const fixture = JSON.parse(rawFixture);
            if (fixture) {
              return request({
                method: 'post',
                json: true,
                uri: 'http://localhost:2525/imposters',
                body: fixture,
              });
            } else {
              throw new Error('no fixture associated with spec file', test.file);
            }
          } catch (e) {
            request({
              method: 'delete',
              json: true,
              uri: 'http://localhost:2525/imposters',
              body: {
                port: 8084,
                protocol: 'http',
                stubs: [
                  {
                    responses: [
                      {
                        proxy: {
                          to: 'http://localhost:18084',
                          mode: 'proxyTransparent',
                          predicateGenerators: [
                            {
                              matches: { method: true, path: true, query: true },
                            },
                          ],
                        },
                      },
                    ],
                  },
                ],
              },
            });
          }
        }
      });
    });
  },
  /**
   * Hook that gets executed _before_ a hook within the suite starts (e.g. runs before calling
   * beforeEach in Mocha)
   */
  // beforeHook: function () {
  // },
  /**
   * Hook that gets executed _after_ a hook within the suite ends (e.g. runs after calling
   * afterEach in Mocha)
   */
  // afterHook: function () {
  // },
  /**
   * Function to be executed after a test (in Mocha/Jasmine) or a step (in Cucumber) ends.
   * @param {Object} test test details
   */
  afterTest: function(test) {
    const run = testRuns.find(t => t.file === test.file);
    if (flags.isRecordMode) {
      return request.get('http://localhost:2525/imposters/8084?replayable=true&removeProxies=true').then(res => {
        fs.writeFileSync(run.fixtureFile, res);
      });
    }
    if (flags.captureLogs) {
      run.browserLogs = browser.log('browser');
    }
  },
  /**
   * Hook that gets executed after the suite has ended
   * @param {Object} suite suite details
   */
  // afterSuite: function (suite) {
  // },

  /**
   * Runs after a WebdriverIO command gets executed
   * @param {String} commandName hook command name
   * @param {Array} args arguments that command would receive
   * @param {Number} result 0 - command success, 1 - command error
   * @param {Object} error error object if any
   */
  // afterCommand: function (commandName, args, result, error) {
  // },
  /**
   * Gets executed after all tests are done. You still have access to all global variables from
   * the test.
   * @param {Number} result 0 - test pass, 1 - test fail
   * @param {Array.<Object>} capabilities list of capabilities details
   * @param {Array.<String>} specs List of spec file paths that ran
   */
  after: function (result, capabilities, specs) {
    if (flags.captureLogs && browser.sessionId) {
      const logJson = testRuns.map(run => run.browserLogs);
      const outPath = path.resolve(__dirname, './' + browser.sessionId + '.browser-logs.json');
      fs.writeFileSync(outPath, JSON.stringify(logJson, null, 4));
      console.log('Browser logs written to ' + outPath);
    }
  },
  /**
   * Gets executed right after terminating the webdriver session.
   * @param {Object} config wdio configuration object
   * @param {Array.<Object>} capabilities list of capabilities details
   * @param {Array.<String>} specs List of spec file paths that ran
   */
  // afterSession: function (config, capabilities, specs) {
  // },
  /**
   * Gets executed after all workers got shut down and the process is about to exit.
   * @param {Object} exitCode 0 - success, 1 - fail
   * @param {Object} config wdio configuration object
   * @param {Array.<Object>} capabilities list of capabilities details
   */
  onComplete: function(exitCode, config, capabilities) {
    if (mountebankProcess) {
      logMountebank('Killing mountebank process');
      mountebankProcess.kill();
    }
  },
};
