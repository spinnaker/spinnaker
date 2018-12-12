'use strict';

import { SETTINGS } from 'core/config/settings';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.analytics.service', [require('angulartics'), require('angulartics-google-analytics')])
  .run(function($window) {
    if (SETTINGS.analytics.ga || SETTINGS.analytics.customConfig) {
      $window.addEventListener('load', () => {
        if (SETTINGS.analytics.customConfig) {
          $window.ga('create', SETTINGS.analytics.ga, SETTINGS.analytics.customConfig);
        } else {
          $window.ga('create', SETTINGS.analytics.ga, 'auto');
        }
      });
    }
  });
