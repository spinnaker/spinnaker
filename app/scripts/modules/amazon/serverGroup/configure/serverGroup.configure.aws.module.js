'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.amazon.serverGroup.configure.module', [
  require('../serverGroup.transformer.js'),
  require('./wizard/templateSelection/deployInitializer.controller.js'),
  require('./wizard/location/ServerGroupBasicSettings.controller.js'),
  require('./wizard/securityGroups/securityGroupSelector.directive.js'),
  require('./wizard/securityGroups/securityGroupsRemoved.directive.js'),
  require('./wizard/loadBalancers/loadBalancerSelector.directive.js'),
  require('./wizard/capacity/capacitySelector.directive.js'),
  require('./wizard/capacity/targetHealthyPercentageSelector.directive.js'),
  require('./wizard/zones/azRebalanceSelector.directive.js'),
  require('./wizard/zones/availabilityZoneSelector.directive.js'),
  require('./wizard/advancedSettings/advancedSettings.component.js'),
]);
