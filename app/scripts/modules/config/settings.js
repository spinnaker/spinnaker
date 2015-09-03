'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.settings', [])
  .constant('settings', window.spinnakerSettings)
  .name;

window.tracking = {
  enabled: false, // set to true to enable GA tracking
  key: 'key goes here',
};
