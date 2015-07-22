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
      './node_modules/jquery/dist/jquery.js',
      './node_modules/angular/angular.js',
      './node_modules/angular-mocks/angular-mocks.js',
      './node_modules/phantomjs-polyfill/bind-polyfill.js',
      //'app/**/*.spec.js',
      'test/test_index.js'
    ],

    preprocessors: {
      //'app/**/*.spec.js': ['webpack'],
      'test/test_index.js': ['webpack', 'sourcemap'],
    },

    webpack: {
      module: {
        loaders: [
          {
            test: /jquery\.js$/,
            loader: 'expose?jQuery',
          },
          {
            test: /\.css$/,
            loader: 'style!css',
          },
          {
            test: /\.js$/,
            loader: 'babel',
            exclude: /node_modlules/,
          },
          {
            test: /\.less$/,
            loader: 'style!css!less',
          },
          {
            test: /\.(woff|otf|ttf|eot|svg|png|gif)(.*)?$/,
            loader: 'file',
          },
          {
            test: /\.html$/,
            loader: 'ngtemplate?relativeTo=' + __dirname + '/!html'
          },
        ]
      },
      devtool: 'inline-source-map',
      watch: true,
    },

    webpackMiddleware: {
      noInfo: true,
    },

    plugins: [
      require('karma-webpack'),
      require('karma-jasmine'),
      require('karma-phantomjs-launcher'),
      require('karma-chrome-launcher'),
      require('karma-junit-reporter'),
      require('karma-mocha-reporter'),
      require('karma-sourcemap-loader'),
    ],

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
      'PhantomJS',
      //'Chrome',
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

    client: {
      captureConsole: true,
    }
  });
};
