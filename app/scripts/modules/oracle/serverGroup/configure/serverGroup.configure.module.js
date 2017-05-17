'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.oraclebmcs.serverGroup.configure', [
  require('./wizard/basicSettings/basicSettings.controller.js'),
  require('./wizard/capacity/capacitySelector.component.js'),
  require('./serverGroupConfiguration.service.js')
]);
