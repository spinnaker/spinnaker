'use strict';

var HappyPack = require('happypack');
var happyThreadPool = HappyPack.ThreadPool({ size: 3 });

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

    webpack: {
      resolve: {
        extensions: ['', '.js', '.ts']
      },
      module: {
        loaders: [
          {
            test: /jquery\.js$/,
            loader: 'expose?jQuery',
          },
          {
            test: /\.ts$/,
            loader: 'ts'
          },
          {
            test: /\.css$/,
            loader: 'style!css',
          },
          {
            test: /\.js$/,
            loader: 'happypack/loader?id=jstest',
            exclude: /node_modules(?!\/clipboard)/,
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
          {
            test: /\.json$/,
            loader: 'json-loader'
          },
        ],
        postLoaders: [
          {
            test: /\.js$/,
            exclude: /(test|node_modules|bower_components)\//,
            loader: 'istanbul-instrumenter'
          }
        ]
      },
      plugins: [
        new HappyPack({
          id: 'jstest',
          loaders: [ 'ng-annotate!angular!babel!envify!eslint' ],
          threadPool: happyThreadPool,
          cacheContext: {
            env: process.env,
          },
        }),
      ],
      watch: true,
    },

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
