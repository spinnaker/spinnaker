'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.cf', [
  require('./serverGroupConfiguration.service.js').name,
  require('./wizard/ServerGroupBasicSettings.controller.js').name,
  require('./wizard/ServerGroupLoadBalancers.controller.js').name,
  require('./wizard/ServerGroupServices.controller.js').name,
  require('./wizard/ServerGroupEnvs.controller.js').name,
  require('./wizard/ServerGroupArtifactSettings.controller.js').name,
  require('./wizard/ServerGroupAdvanced.controller.js').name,
  require('./serverGroupConfiguration.service.js').name,
  require('./serverGroupBasicSettingsSelector.directive.js').name,
  require('./serverGroupLoadBalancersSelector.directive.js').name,
  require('./serverGroupServicesSelector.directive.js').name,
  require('./serverGroupEnvsSelector.directive.js').name,
  require('./serverGroupArtifactSettingsSelector.directive.js').name,
  require('./serverGroupAdvancedSelector.directive.js').name,

]);
