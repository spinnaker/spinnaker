'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.oracle.serverGroup.configure', [
  require('./wizard/basicSettings/basicSettings.controller.js').name,
  require('./wizard/capacity/capacitySelector.component.js').name,
  require('./serverGroupConfiguration.service.js').name,
]);
