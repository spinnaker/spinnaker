'use strict';

import {SETTINGS} from 'core/config/settings';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.analytics', [
    require('angulartics'),
    require('angulartics-google-analytics'),
  ])
  .run(function ($window) {
    if (SETTINGS.analytics.ga) {
      $window.ga('create', SETTINGS.analytics.ga, 'auto');
    }
  });
