require('ts-node/register');

const config = {
  specs: ['test/functional/tests/**/*.spec.ts'],
  maxInstances: 1,
  capabilities: [
    {
      maxInstances: 1,
      browserName: 'chrome',
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
  reporters: ['dot'],
  jasmineNodeOpts: {
    defaultTimeoutInterval: 60000,
  },
  suites: {
    google: ['./src/google/**/*.spec.ts'],
  },

  beforeTest: function(test) {
    browser.windowHandleSize({ width: 1280, height: 1024 });
  },
};

exports.config = config;
