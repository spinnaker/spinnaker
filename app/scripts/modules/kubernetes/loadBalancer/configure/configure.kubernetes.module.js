'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.loadBalancer.configure.kubernetes', [
  require('core/account/account.module.js'),
  require('./wizard/upsert.controller.js'),
  require('./wizard/ports.controller.js'),
  require('./wizard/advancedSettings.controller.js'),
]);
