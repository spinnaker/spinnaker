'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.loadBalancer.configure.kubernetes', [
  require('./wizard/upsert.controller.js'),
  require('./wizard/ports.controller.js'),
  require('./wizard/advancedSettings.controller.js'),
]);
