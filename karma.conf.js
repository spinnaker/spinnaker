'use strict';

const path = require('path');
const webpackCommon = require('./webpack.common');

module.exports = function(config) {
  const reportCoverage = config.coverage === true;
  const options = {
    autoWatch: true,

    // base path, that will be used to resolve files and exclude
    basePath: '',

    // testing framework to use (jasmine/mocha/qunit/...)
    frameworks: ['jasmine'],

    // list of files / patterns to load in the browser
    files: [{ pattern: './karma-shim.js', watched: false }],

    preprocessors: {
      './karma-shim.js': ['webpack'],
    },

    webpack: webpackCommon(true, reportCoverage),

    webpackMiddleware: {
      noInfo: true,
    },

    customLaunchers: {
      Chrome_travis_ci: {
        base: 'Chrome',
        flags: ['--no-sandbox'],
      },
      ChromeActive: {
        base: 'Chrome',
        flags: ['--override-plugin-power-saver-for-testing=0'],
      },
    },

    plugins: [
      require('karma-webpack'),
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-junit-reporter'),
      require('karma-mocha-reporter'),
    ],

    // list of files / patterns to exclude
    exclude: [],

    // web server port
    port: 8081,

    browsers: [process.env.TRAVIS ? 'Chrome_travis_ci' : 'ChromeActive'],

    colors: true,

    // level of logging
    // possible values: LOG_DISABLE || LOG_ERROR || LOG_WARN || LOG_INFO || LOG_DEBUG
    logLevel: config.DEBUG,

    // jUnit Report output
    reporters: ['progress', 'mocha'],

    // the default configuration
    junitReporter: {
      outputFile: 'test-results.xml',
    },

    mochaReporter: {
      ignoreSkipped: true,
    },

    client: {
      args: reportCoverage ? ['--coverage'] : [],
      captureConsole: true,
    },

    browserNoActivityTimeout: 200000,
  };

  if (reportCoverage) {
    options.devtool = 'inline-source-map';

    options.plugins.push(require('karma-coverage'), require('karma-remap-coverage'));

    options.coverageReporter = {
      type: 'in-memory',
    };

    options.reporters.push('coverage', 'remap-coverage');

    options.remapCoverageReporter = {
      html: './coverage/html',
      lcovonly: './coverage/lcov.info', // for codecov
      'text-summary': null, // prints to console
    };
  }

  config.set(options);
};
