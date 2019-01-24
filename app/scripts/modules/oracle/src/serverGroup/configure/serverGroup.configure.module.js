'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.oracle.serverGroup.configure', [
  require('./wizard/basicSettings/basicSettings.controller').name,
  require('./wizard/capacity/capacitySelector.component').name,
  require('./serverGroupConfiguration.service').name,
]);
