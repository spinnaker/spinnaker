'use strict';

const angular = require('angular');

import { GCE_LOAD_BALANCING_POLICY_SELECTOR } from './wizard/loadBalancingPolicy/loadBalancingPolicySelector.component';
import { GCE_AUTOHEALING_POLICY_SELECTOR } from './wizard/autoHealingPolicy/autoHealingPolicySelector.component';
import { GCE_CACHE_REFRESH } from 'google/cache/cacheRefresh.component';
import { GCE_CUSTOM_INSTANCE_CONFIGURER } from './wizard/customInstance/customInstanceConfigurer.component';
import { GCE_DISK_CONFIGURER } from './wizard/advancedSettings/diskConfigurer.component';
import { GCE_ACCELERATOR_CONFIGURER } from './wizard/advancedSettings/acceleratorConfigurer.component';

module.exports = angular.module('spinnaker.serverGroup.configure.gce', [
  require('../../autoscalingPolicy/components/basicSettings/basicSettings.component').name,
  require('../../autoscalingPolicy/components/metricSettings/metricSettings.component').name,
  GCE_LOAD_BALANCING_POLICY_SELECTOR,
  GCE_AUTOHEALING_POLICY_SELECTOR,
  require('./../../instance/custom/customInstanceBuilder.gce.service').name,
  GCE_CACHE_REFRESH,
  GCE_CUSTOM_INSTANCE_CONFIGURER,
  GCE_DISK_CONFIGURER,
  GCE_ACCELERATOR_CONFIGURER,
  require('../serverGroup.transformer').name,
  require('./serverGroupConfiguration.service').name,
  require('./wizard/advancedSettings/advancedSettingsSelector.directive').name,
  require('./wizard/capacity/advancedCapacitySelector.component').name,
  require('./wizard/capacity/simpleCapacitySelector.component').name,
  require('./wizard/loadBalancers/loadBalancerSelector.directive').name,
  require('./wizard/location/basicSettings.controller').name,
  require('./wizard/securityGroups/securityGroupsRemoved.directive').name,
  require('./wizard/securityGroups/securityGroupSelector.directive').name,
  require('./wizard/zones/regionalSelector.directive').name,
  require('./wizard/zones/zoneSelector.directive').name,
]);
