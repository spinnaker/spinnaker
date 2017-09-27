'use strict';

const angular = require('angular');

import { GCE_LOAD_BALANCING_POLICY_SELECTOR } from './wizard/loadBalancingPolicy/loadBalancingPolicySelector.component';
import { GCE_AUTOHEALING_POLICY_SELECTOR } from './wizard/autoHealingPolicy/autoHealingPolicySelector.component';
import { GCE_CACHE_REFRESH } from 'google/cache/cacheRefresh.component';
import { GCE_CUSTOM_INSTANCE_CONFIGURER } from './wizard/customInstance/customInstanceConfigurer.component';
import { GCE_DISK_CONFIGURER } from './wizard/advancedSettings/diskConfigurer.component';

module.exports = angular.module('spinnaker.serverGroup.configure.gce', [
  require('../../autoscalingPolicy/components/basicSettings/basicSettings.component.js').name,
  require('../../autoscalingPolicy/components/metricSettings/metricSettings.component.js').name,
  GCE_LOAD_BALANCING_POLICY_SELECTOR,
  GCE_AUTOHEALING_POLICY_SELECTOR,
  require('./../../instance/custom/customInstanceBuilder.gce.service.js').name,
  GCE_CACHE_REFRESH,
  GCE_CUSTOM_INSTANCE_CONFIGURER,
  GCE_DISK_CONFIGURER,
  require('../serverGroup.transformer.js').name,
  require('./serverGroupConfiguration.service.js').name,
  require('./wizard/advancedSettings/advancedSettingsSelector.directive.js').name,
  require('./wizard/capacity/advancedCapacitySelector.component.js').name,
  require('./wizard/capacity/simpleCapacitySelector.component.js').name,
  require('./wizard/loadBalancers/loadBalancerSelector.directive.js').name,
  require('./wizard/location/basicSettings.controller.js').name,
  require('./wizard/securityGroups/securityGroupsRemoved.directive.js').name,
  require('./wizard/securityGroups/securityGroupSelector.directive.js').name,
  require('./wizard/zones/regionalSelector.directive.js').name,
  require('./wizard/zones/zoneSelector.directive.js').name,
]);
