'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.settings', [])
  .constant('settings', window.spinnakerSettings)
  .name;
