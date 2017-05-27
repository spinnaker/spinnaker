import { module } from 'angular';

import { AZ_REBALANCE_SELECTOR } from './wizard/zones/azRebalanceSelector.component';
import { CAPACITY_SELECTOR } from './wizard/capacity/capacitySelector.component';
import { HEALTH_PERCENT_SELECTOR } from './wizard/capacity/targetHealthyPercentageSelector.component';
import { LOAD_BALANCER_SELECTOR } from './wizard/loadBalancers/loadBalancerSelector.component';
import { SECURITY_GROUPS_REMOVED } from './wizard/securityGroups/securityGroupsRemoved.component';

export const SERVER_GROUP_CONFIGURE_MODULE = 'spinnaker.amazon.serverGroup.configure.module';
module(SERVER_GROUP_CONFIGURE_MODULE, [
  AZ_REBALANCE_SELECTOR,
  CAPACITY_SELECTOR,
  HEALTH_PERCENT_SELECTOR,
  LOAD_BALANCER_SELECTOR,
  SECURITY_GROUPS_REMOVED,
  require('../serverGroup.transformer.js'),
  require('./wizard/templateSelection/deployInitializer.controller.js'),
  require('./wizard/location/ServerGroupBasicSettings.controller.js'),
  require('./wizard/securityGroups/securityGroupSelector.directive.js'),
  require('./wizard/zones/availabilityZoneSelector.directive.js'),
  require('./wizard/advancedSettings/advancedSettings.component.js'),
]);
