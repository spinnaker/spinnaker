'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.configure', [
  require('core/account/account.module.js'),
  require('./wizard/deployInitializer.controller.js'),
  require('./wizard/basicSettings/ServerGroupBasicSettings.controller.js'),
  require('./wizard/loadBalancers/ServerGroupLoadBalancers.controller.js'),
  require('./wizard/ServerGroupInstanceArchetype.controller.js'),
  require('./wizard/ServerGroupInstanceType.controller.js'),
  require('./wizard/securityGroup/ServerGroupSecurityGroups.controller.js'),
  require('./wizard/advancedSettings/ServerGroupAdvancedSettings.controller.js'),
  require('./wizard/loadBalancers/serverGroupLoadBalancersSelector.directive.js'),
  require('./wizard/capacity/capacitySelector.directive.js'),
  require('./wizard/securityGroup/serverGroupSecurityGroupsSelector.directive.js'),
  require('../serverGroup.transformer.js'),
  require('./wizard/advancedSettings/advancedSettingsSelector.directive.js'),
  require('core/serverGroup/configure/common/instanceArchetypeSelector.js'),
  require('core/serverGroup/configure/common/instanceTypeSelector.js'),
  require('./wizard/networkSettings/ServerGroupNetworkSettings.controller.js'),
  require('./wizard/networkSettings/ServerGroupNetworkSettingsSelector.directive.js')
]);
