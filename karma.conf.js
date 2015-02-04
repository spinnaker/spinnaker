// Karma configuration
// http://karma-runner.github.io/0.12/config/configuration-file.html
// Generated on 2014-07-17 using
// generator-karma 0.8.2

module.exports = function(config) {
  config.set({
    // enable / disable watching file and executing tests whenever any file changes
    autoWatch: true,

    // base path, that will be used to resolve files and exclude
    basePath: '',

    // testing framework to use (jasmine/mocha/qunit/...)
    frameworks: ['jasmine'],

    // list of files / patterns to load in the browser
    files: [
      'node_modules/es5-shim/es5-shim.js',
      'test/helpers/**/*.js',
      'dist/scripts/vendor*.js',
      'bower_components/angular-mocks/angular-mocks.js',
      'dist/scripts/*.js',
      'test/poly/**/*.js',
      'test/mock/**/*.js',
      'test/spec/**/*.js',
      'app/scripts/testHelpers/*.js',
      'app/scripts/modules/**/*.spec.js',
      'app/scripts/controllers/**/*.spec.js',
      'app/scripts/services/**/*.spec.js',
      'test/fixture/**/*.js',
      'app/views/**/*.html',
      'app/scripts/modules/**/*.html'
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
      'PhantomJS'
    ],

    // Which plugins to enable
    plugins: [
      'karma-phantomjs-launcher',
      'karma-chrome-launcher',
      'karma-junit-reporter',
      'karma-mocha-reporter',
      'karma-jasmine',
      'karma-ng-html2js-preprocessor'
    ],

    preprocessors: {
      '**/[modules|views]**/*.html': ['ng-html2js'],
    },


    ngHtml2JsPreprocessor: {
      // strip this from the file path
      stripPrefix: 'app/'
    },

    // Continuous Integration mode
    // if true, it capture browsers, run tests and exit
    singleRun: false,

    colors: true,

    // level of logging
    // possible values: LOG_DISABLE || LOG_ERROR || LOG_WARN || LOG_INFO || LOG_DEBUG
    logLevel: config.DEBUG,

    // jUnit Report output
    reporters: ['progress', 'junit', 'mocha'],

    // the default configuration
    junitReporter: {
      outputFile: 'test-results.xml'
    }
  });
};
