'use strict';

var webpackConf = require('./webpack.config.js');

module.exports = function(config) {
  config.set({
    autoWatch: true,

    // base path, that will be used to resolve files and exclude
    basePath: '',

    // testing framework to use (jasmine/mocha/qunit/...)
    frameworks: ['jasmine'],

    // list of files / patterns to load in the browser
    files: [
      './node_modules/phantomjs-polyfill/bind-polyfill.js',
      'app/scripts/**/*.spec.js',
    ],

    preprocessors: {
      'app/scripts/**/*.spec.js': ['webpack'],
    },

    webpack: {
      module: webpackConf.module,
      watch: true,
    },

    webpackServer: {
      noInfo: true,
    },

    // list of files / patterns to exclude
    exclude: [],

    // web server port
    port: 8081,

    // Start these browsers, currently available:
    // - Chrome
    // - ChromeCanary
    // - Firefox
    // - Opera
    // - Safari (only Mac)
    // - PhantomJS
    // - IE (only Windows)
    browsers: [
      'PhantomJs',
      'Chrome',
    ],

    colors: true,

    // level of logging
    // possible values: LOG_DISABLE || LOG_ERROR || LOG_WARN || LOG_INFO || LOG_DEBUG
    logLevel: config.DEBUG,

    // jUnit Report output
    reporters: ['progress'],

    // the default configuration
    junitReporter: {
      outputFile: 'test-results.xml'
    },

    client: {
      captureConsole: true,
    }
  });
};
