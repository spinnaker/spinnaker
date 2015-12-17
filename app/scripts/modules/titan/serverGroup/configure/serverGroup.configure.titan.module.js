'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.titan', [
  require('../../../core/account/account.module.js'),
  require('./wizard/deployInitializer.controller.js'),
  require('../../../core/cache/infrastructureCaches.js'),
  require('./serverGroupConfiguration.service.js'),
  require('./wizard/ServerGroupBasicSettings.controller.js'),
  require('./wizard/ServerGroupResources.controller.js'),
  require('./wizard/ServerGroupCapacity.controller.js'),
  require('./wizard/ServerGroupParameters.controller.js'),
  require('./serverGroupBasicSettingsSelector.directive.js'),
  require('./serverGroupCapacitySelector.directive.js'),
]);
