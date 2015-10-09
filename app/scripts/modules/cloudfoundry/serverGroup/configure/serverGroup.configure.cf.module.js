'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.cf', [
  require('../../../core/account/account.module.js'),
  require('../../../core/cache/infrastructureCaches.js'),
  require('./wizard/deployInitializer.controller.js'),
  require('./wizard/ServerGroupBasicSettings.controller.js'),
  require('./serverGroupConfiguration.service.js'),
  require('./serverGroupBasicSettingsSelector.directive.js'),

])
.name;
