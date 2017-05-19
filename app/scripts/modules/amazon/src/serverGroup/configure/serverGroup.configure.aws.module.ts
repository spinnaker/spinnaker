import { module } from 'angular';

import { AZ_REBALANCE_SELECTOR } from './wizard/zones/azRebalanceSelector.component';
import { SECURITY_GROUPS_REMOVED } from './wizard/securityGroups/securityGroupsRemoved.component';
import { CAPACITY_SELECTOR } from './wizard/capacity/capacitySelector.component';
import { HEALTH_PERCENT_SELECTOR } from './wizard/capacity/targetHealthyPercentageSelector.component';

export const SERVER_GROUP_CONFIGURE_MODULE = 'spinnaker.amazon.serverGroup.configure.module';
module(SERVER_GROUP_CONFIGURE_MODULE, [
  require('../serverGroup.transformer.js'),
  require('./wizard/templateSelection/deployInitializer.controller.js'),
  require('./wizard/location/ServerGroupBasicSettings.controller.js'),
  require('./wizard/securityGroups/securityGroupSelector.directive.js'),
  SECURITY_GROUPS_REMOVED,
  require('./wizard/loadBalancers/loadBalancerSelector.directive.js'),
  CAPACITY_SELECTOR,
  HEALTH_PERCENT_SELECTOR,
  AZ_REBALANCE_SELECTOR,
  require('./wizard/zones/availabilityZoneSelector.directive.js'),
  require('./wizard/advancedSettings/advancedSettings.component.js'),
]);
