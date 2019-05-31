'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.configure', [
  require('./wizard/basicSettings/ServerGroupBasicSettings.controller').name,
  require('./wizard/loadBalancers/ServerGroupLoadBalancers.controller').name,
  require('./wizard/ServerGroupInstanceArchetype.controller').name,
  require('./wizard/ServerGroupInstanceType.controller').name,
  require('./wizard/securityGroup/ServerGroupSecurityGroups.controller').name,
  require('./wizard/advancedSettings/ServerGroupAdvancedSettings.controller').name,
  require('./wizard/loadBalancers/serverGroupLoadBalancersSelector.directive').name,
  require('./wizard/capacity/capacitySelector.directive').name,
  require('./wizard/securityGroup/serverGroupSecurityGroupsSelector.directive').name,
  require('../serverGroup.transformer').name,
  require('./wizard/advancedSettings/advancedSettingsSelector.directive').name,
  require('./wizard/networkSettings/ServerGroupNetworkSettings.controller').name,
  require('./wizard/networkSettings/ServerGroupNetworkSettingsSelector.directive').name,
  require('./wizard/zones/zoneSelector.directive').name,
  require('./wizard/tags/tagsSelector.directive').name,
]);
