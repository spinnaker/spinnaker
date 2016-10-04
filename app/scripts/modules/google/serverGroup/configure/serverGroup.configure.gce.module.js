'use strict';

let angular = require('angular');

import gceLoadBalancingPolicySelector from './wizard/loadBalancingPolicy/loadBalancingPolicySelector.component';

module.exports = angular.module('spinnaker.serverGroup.configure.gce', [
  require('../../autoscalingPolicy/components/basicSettings/basicSettings.component.js'),
  require('../../autoscalingPolicy/components/metricSettings/metricSettings.component.js'),
  require('../../../core/account/account.module.js'),
  require('../../../core/cache/infrastructureCaches.js'),
  require('../../../core/serverGroup/configure/common/v2instanceArchetypeSelector.directive.js'),
  require('../../../core/serverGroup/configure/common/v2InstanceTypeSelector.directive.js'),
  gceLoadBalancingPolicySelector,
  require('./../../instance/custom/customInstanceBuilder.gce.service.js'),
  require('../serverGroup.transformer.js'),
  require('./serverGroupConfiguration.service.js'),
  require('./wizard/advancedSettings/advancedSettingsSelector.directive.js'),
  require('./wizard/capacity/advancedCapacitySelector.component.js'),
  require('./wizard/capacity/simpleCapacitySelector.component.js'),
  require('./wizard/loadBalancers/loadBalancerSelector.directive.js'),
  require('./wizard/location/basicSettings.controller.js'),
  require('./wizard/templateSelection/deployInitializer.controller.js'),
  require('./wizard/securityGroups/securityGroupsRemoved.directive.js'),
  require('./wizard/securityGroups/securityGroupSelector.directive.js'),
  require('./wizard/zones/regionalSelector.directive.js'),
  require('./wizard/zones/zoneSelector.directive.js'),
]);
