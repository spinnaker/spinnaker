'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.titus', [
  require('./wizard/deployInitializer.controller.js').name,
  require('./serverGroupConfiguration.service.js').name,
  require('./wizard/ServerGroupBasicSettings.controller.js').name,
  require('./wizard/ServerGroupResources.controller.js').name,
  require('./wizard/ServerGroupCapacity.controller.js').name,
  require('./wizard/ServerGroupParameters.controller.js').name,
  require('./serverGroupBasicSettingsSelector.directive.js').name,
  require('./serverGroupCapacitySelector.directive.js').name,
]);
