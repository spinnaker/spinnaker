'use strict';

const path = require('path');
const webpackCommon = require('./webpack.common');
const webpackConfig = webpackCommon(true);

module.exports = function (config) {
  config.set({
    autoWatch: true,

    // base path, that will be used to resolve files and exclude
    basePath: '',

    // testing framework to use (jasmine/mocha/qunit/...)
    frameworks: ['jasmine'],

    // list of files / patterns to load in the browser
    files: [
      {pattern: './karma-shim.js', watched: false}
    ],

    preprocessors: {
      './karma-shim.js': ['webpack']
    },

    webpack: webpackConfig,

    webpackMiddleware: {
      noInfo: true,
    },

    customLaunchers: {
      Chrome_travis_ci: {
        base: 'Chrome',
        flags: ['--no-sandbox']
      },
      ChromeActive: {
        base: 'Chrome',
        flags: ['--override-plugin-power-saver-for-testing=0']
      }
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

    browsers: [
      process.env.TRAVIS ? 'Chrome_travis_ci' : 'ChromeActive',
    ],

    colors: true,

    // level of logging
    // possible values: LOG_DISABLE || LOG_ERROR || LOG_WARN || LOG_INFO || LOG_DEBUG
    logLevel: config.DEBUG,

    // jUnit Report output
    reporters: ['progress', 'mocha'],

    // the default configuration
    junitReporter: {
     outputFile: 'test-results.xml'
    },

    mochaReporter: {
     ignoreSkipped: true,
    },

    client: {
      captureConsole: true,
    },

    browserNoActivityTimeout: 200000
  });
};
