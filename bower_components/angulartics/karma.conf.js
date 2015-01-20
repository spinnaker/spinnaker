module.exports = function(config) {
  'use strict';
  
  config.set({

    basePath: './',

    frameworks: ["jasmine"],

    files: [
      'components/angular/angular.js',
      'components/angular-route/angular-route.js',
      'components/angular-ui-router/release/angular-ui-router.js',
      'components/angular-mocks/angular-mocks.js',
      'src/**/*.js',
      'test/**/*.js'
    ],

    autoWatch: true,

    browsers: ['PhantomJS']

  });
};
