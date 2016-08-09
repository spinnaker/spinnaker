'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.gce', [
  require('../../../core/account/account.module.js'),
  require('../../../core/cache/infrastructureCaches.js'),
  require('../../../core/serverGroup/configure/common/v2instanceArchetypeSelector.directive.js'),
  require('../../../core/serverGroup/configure/common/v2InstanceTypeSelector.directive.js'),
  require('../serverGroup.transformer.js'),
  require('./serverGroupConfiguration.service.js'),
  require('./wizard/templateSelection/deployInitializer.controller.js'),
  require('./wizard/location/basicSettings.controller.js'),
  require('./wizard/loadBalancers/loadBalancerSelector.directive.js'),
  require('./wizard/securityGroups/securityGroupSelector.directive.js'),
  require('./wizard/securityGroups/securityGroupsRemoved.directive.js'),
  require('./wizard/capacity/capacitySelector.directive.js'),
  require('./wizard/zones/regionalSelector.directive.js'),
  require('./wizard/zones/zoneSelector.directive.js'),
  require('./wizard/advancedSettings/advancedSettingsSelector.directive.js'),
  require('./wizard/loadBalancingPolicy/loadBalancingPolicySelector.component.js'),
  require('./../../instance/custom/customInstanceBuilder.gce.service.js'),
  require('../../autoscalingPolicy/components/basicSettings/basicSettings.component.js'),
  require('../../autoscalingPolicy/components/metricSettings/metricSettings.component.js')
]);
