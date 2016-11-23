'use strict';

let angular = require('angular');

import {GCE_LOAD_BALANCING_POLICY_SELECTOR} from './wizard/loadBalancingPolicy/loadBalancingPolicySelector.component';
import {GCE_AUTOHEALING_POLICY_SELECTOR} from './wizard/autoHealingPolicy/autoHealingPolicySelector.component';

module.exports = angular.module('spinnaker.serverGroup.configure.gce', [
  require('../../autoscalingPolicy/components/basicSettings/basicSettings.component.js'),
  require('../../autoscalingPolicy/components/metricSettings/metricSettings.component.js'),
  require('core/account/account.module.js'),
  require('core/cache/infrastructureCaches.js'),
  require('core/serverGroup/configure/common/v2instanceArchetypeSelector.directive.js'),
  require('core/serverGroup/configure/common/v2InstanceTypeSelector.directive.js'),
  GCE_LOAD_BALANCING_POLICY_SELECTOR,
  GCE_AUTOHEALING_POLICY_SELECTOR,
  require('./../../instance/custom/customInstanceBuilder.gce.service.js'),
  require('google/cache/cacheRefresh.component.js'),
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
