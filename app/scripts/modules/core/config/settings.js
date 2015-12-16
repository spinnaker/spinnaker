'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.config.settings', [])
  .constant('settings', window.spinnakerSettings);
