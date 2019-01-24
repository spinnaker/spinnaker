'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.loadBalancer.configure.kubernetes', [
  require('./wizard/upsert.controller').name,
  require('./wizard/ports.controller').name,
  require('./wizard/advancedSettings.controller').name,
]);
