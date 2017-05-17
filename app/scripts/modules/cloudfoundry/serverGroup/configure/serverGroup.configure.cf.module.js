'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.cf', [
  require('core/account/account.module.js'),
  require('./serverGroupConfiguration.service.js'),
  require('./wizard/deployInitializer.controller.js'),
  require('./wizard/ServerGroupBasicSettings.controller.js'),
  require('./wizard/ServerGroupLoadBalancers.controller.js'),
  require('./wizard/ServerGroupServices.controller.js'),
  require('./wizard/ServerGroupEnvs.controller.js'),
  require('./wizard/ServerGroupArtifactSettings.controller.js'),
  require('./wizard/ServerGroupAdvanced.controller.js'),
  require('./serverGroupConfiguration.service.js'),
  require('./serverGroupBasicSettingsSelector.directive.js'),
  require('./serverGroupLoadBalancersSelector.directive.js'),
  require('./serverGroupServicesSelector.directive.js'),
  require('./serverGroupEnvsSelector.directive.js'),
  require('./serverGroupArtifactSettingsSelector.directive.js'),
  require('./serverGroupAdvancedSelector.directive.js'),

]);
