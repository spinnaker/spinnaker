'use strict';

const path = require('path');
const webpackCommon = require('./webpack.common');
const webpackConfig = webpackCommon(true);

module.exports = function(config) {
  config.set({
    autoWatch: true,

    // base path, that will be used to resolve files and exclude
    basePath: '',

    // testing framework to use (jasmine/mocha/qunit/...)
    frameworks: ['jasmine'],

    // list of files / patterns to load in the browser
    files: [
      './node_modules/jquery/dist/jquery.js',
      './node_modules/angular/angular.js',
      './node_modules/angular-mocks/angular-mocks.js',
      'settings.js',
      'test/test_index.js'
    ],

    preprocessors: {
      './**/*.spec.js': ['webpack'],
      './**/*.spec.ts': ['webpack'],
      'settings.js': ['webpack'],
      'test/**/*.js': ['webpack'],
    },

    webpack: webpackConfig,

    webpackMiddleware: {
      noInfo: true,
    },

    customLaunchers: {
      Chrome_travis_ci: {
        base: 'Chrome',
        flags: ['--no-sandbox']
      }
    },

    plugins: [
      require('karma-webpack'),
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-junit-reporter'),
      require('karma-mocha-reporter'),
      require('karma-jenkins-reporter'),
      require('karma-coverage'),
    ],

    // list of files / patterns to exclude
    exclude: [],

    // web server port
    port: 8081,

    browsers: [
      process.env.TRAVIS ? 'Chrome_travis_ci' : 'Chrome',
    ],

    colors: true,

    // level of logging
    // possible values: LOG_DISABLE || LOG_ERROR || LOG_WARN || LOG_INFO || LOG_DEBUG
    logLevel: config.DEBUG,

    // jUnit Report output
    reporters: ['progress', 'mocha', 'coverage'],

    // the default configuration
    junitReporter: {
      outputFile: 'test-results.xml'
    },

    mochaReporter: {
      ignoreSkipped: true,
    },

    coverageReporter: {
      type : 'html',
      dir : 'coverage/'
    },

    jenkinsReporter: {
      outputFile: 'test-results.xml',
      suite: 'com.netflix.spinnaker.deck',
      classnameSuffix: 'ui-test'
    },

    client: {
      captureConsole: true,
    }
  });
};
