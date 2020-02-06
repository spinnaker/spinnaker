'use strict';

const ForkTsCheckerWebpackPlugin = require('fork-ts-checker-webpack-plugin');
const { TypedCssModulesPlugin } = require('typed-css-modules-webpack-plugin');

const prodWebpackConfig = require('./webpack.config')();
const webpackConfig = {
  mode: 'development',
  module: prodWebpackConfig.module,
  resolve: prodWebpackConfig.resolve,
  plugins: [
    new TypedCssModulesPlugin({
      globPattern: '**/*.module.css',
    }),
    new ForkTsCheckerWebpackPlugin({ checkSyntacticErrors: true }),
  ],
};

module.exports = function(config) {
  config.set({
    autoWatch: true,

    // base path, that will be used to resolve files and exclude
    basePath: '.',

    // testing framework to use (jasmine/mocha/qunit/...)
    frameworks: ['jasmine'],

    // list of files / patterns to load in the browser
    files: [{ pattern: './karma-shim.js', watched: false }],

    preprocessors: {
      './karma-shim.js': ['webpack', 'sourcemap'],
    },

    webpack: webpackConfig,

    webpackMiddleware: {
      stats: 'minimal',
    },

    customLaunchers: {
      ChromeCI: { base: 'ChromeHeadless', flags: ['--no-sandbox'] },
      ChromeActive: { base: 'Chrome', flags: ['--override-plugin-power-saver-for-testing=0'] },
    },

    plugins: [
      require('karma-webpack'),
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-junit-reporter'),
      require('karma-sourcemap-loader'),
      require('karma-super-dots-reporter'),
      require('karma-mocha-reporter'),
    ],

    // list of files / patterns to exclude
    exclude: [],

    // web server port
    port: 8081,

    browsers: [process.env.TRAVIS || process.env.GITHUB_ACTIONS ? 'ChromeCI' : 'ChromeActive'],

    colors: true,

    // level of logging
    // possible values: LOG_DISABLE || LOG_ERROR || LOG_WARN || LOG_INFO || LOG_DEBUG
    logLevel: config.DEBUG,

    reporters: ['super-dots', 'mocha'],
    mochaReporter: {
      output: 'minimal',
    },

    // put test results in a well known file if 'jenkins' reporter is being used
    junitReporter: {
      outputFile: 'test-results.xml',
    },

    client: {
      captureConsole: true,
      jasmine: {
        random: false,
      },
    },

    browserNoActivityTimeout: 200000,
  });
};
