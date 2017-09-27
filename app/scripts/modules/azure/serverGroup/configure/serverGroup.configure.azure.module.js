'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.configure', [
  require('./wizard/basicSettings/ServerGroupBasicSettings.controller.js').name,
  require('./wizard/loadBalancers/ServerGroupLoadBalancers.controller.js').name,
  require('./wizard/ServerGroupInstanceArchetype.controller.js').name,
  require('./wizard/ServerGroupInstanceType.controller.js').name,
  require('./wizard/securityGroup/ServerGroupSecurityGroups.controller.js').name,
  require('./wizard/advancedSettings/ServerGroupAdvancedSettings.controller.js').name,
  require('./wizard/loadBalancers/serverGroupLoadBalancersSelector.directive.js').name,
  require('./wizard/capacity/capacitySelector.directive.js').name,
  require('./wizard/securityGroup/serverGroupSecurityGroupsSelector.directive.js').name,
  require('../serverGroup.transformer.js').name,
  require('./wizard/advancedSettings/advancedSettingsSelector.directive.js').name,
  require('./wizard/networkSettings/ServerGroupNetworkSettings.controller.js').name,
  require('./wizard/networkSettings/ServerGroupNetworkSettingsSelector.directive.js').name
]);
