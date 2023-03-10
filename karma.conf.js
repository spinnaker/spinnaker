'use strict';

const ForkTsCheckerWebpackPlugin = require('fork-ts-checker-webpack-plugin');
const path = require('path');

const prodWebpackConfig = require('./packages/app/webpack.config')();
const MODULES_ROOT = path.resolve(`${__dirname}/packages`);

const webpackConfig = {
  mode: 'development',
  module: {
    rules: [
      ...prodWebpackConfig.module.rules.filter((rule) => {
        return !(rule.test.source && rule.test.source.includes('html'));
      }),
      {
        test: /\.html$/,
        use: [{ loader: 'ngtemplate-loader?relativeTo=' + path.resolve(__dirname) + '/' }, { loader: 'html-loader' }],
      },
    ],
  },
  resolve: {
    ...prodWebpackConfig.resolve,
    alias: {
      ...prodWebpackConfig.resolve.alias,
      coreImports: path.resolve(`${MODULES_ROOT}/core/src/presentation/less/imports/commonImports.less`),
      amazon: path.resolve(`${MODULES_ROOT}/amazon/src`),
      '@spinnaker/amazon': path.resolve(`${MODULES_ROOT}/amazon/src`),
      appengine: path.resolve(`${MODULES_ROOT}/appengine/src`),
      '@spinnaker/appengine': path.resolve(`${MODULES_ROOT}/appengine/src`),
      azure: path.resolve(`${MODULES_ROOT}/azure/src`),
      '@spinnaker/azure': path.resolve(`${MODULES_ROOT}/azure/src`),
      cloudfoundry: path.resolve(`${MODULES_ROOT}/cloudfoundry/src`),
      '@spinnaker/cloudfoundry': path.resolve(`${MODULES_ROOT}/cloudfoundry/src`),
      cloudrun: path.resolve(`${MODULES_ROOT}/cloudrun/src`),
      '@spinnaker/cloudrun': path.resolve(`${MODULES_ROOT}/cloudrun/src`),
      core: path.resolve(`${MODULES_ROOT}/core/src`),
      '@spinnaker/core': path.resolve(`${MODULES_ROOT}/core/src`),
      dcos: path.resolve(`${MODULES_ROOT}/dcos/src`),
      '@spinnaker/dcos': path.resolve(`${MODULES_ROOT}/dcos/src`),
      docker: path.resolve(`${MODULES_ROOT}/docker/src`),
      '@spinnaker/docker': path.resolve(`${MODULES_ROOT}/docker/src`),
      ecs: path.resolve(`${MODULES_ROOT}/ecs/src`),
      '@spinnaker/ecs': path.resolve(`${MODULES_ROOT}/ecs/src`),
      google: path.resolve(`${MODULES_ROOT}/google/src`),
      '@spinnaker/google': path.resolve(`${MODULES_ROOT}/google/src`),
      huaweicloud: path.resolve(`${MODULES_ROOT}/huaweicloud/src`),
      '@spinnaker/huaweicloud': path.resolve(`${MODULES_ROOT}/huaweicloud/src`),
      kubernetes: path.resolve(`${MODULES_ROOT}/kubernetes/src`),
      '@spinnaker/kubernetes': path.resolve(`${MODULES_ROOT}/kubernetes/src`),
      oracle: path.resolve(`${MODULES_ROOT}/oracle/src`),
      '@spinnaker/oracle': path.resolve(`${MODULES_ROOT}/oracle/src`),
      tencentcloud: path.resolve(`${MODULES_ROOT}/tencentcloud/src`),
      '@spinnaker/tencentcloud': path.resolve(`${MODULES_ROOT}/tencentcloud/src`),
      titus: path.resolve(`${MODULES_ROOT}/titus/src`),
      '@spinnaker/titus': path.resolve(`${MODULES_ROOT}/titus/src`),
    },
  },
  plugins: [new ForkTsCheckerWebpackPlugin({ checkSyntacticErrors: true })],
};

module.exports = function (config) {
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
