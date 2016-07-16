'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.analytics', [
    require('angulartics'),
    require('angulartics-google-analytics'),
  ])
  .run(function ($window, settings) {
    if (settings.analytics && settings.analytics.ga) {
      $window.ga('create', settings.analytics.ga, 'auto');
    }
  });
