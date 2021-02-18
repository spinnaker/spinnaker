import { module } from 'angular';

import { APPENGINE_LOAD_BALANCER_ADVANCED_SETTINGS } from './configure/wizard/advancedSettings.component';
import { APPENGINE_ALLOCATION_CONFIGURATION_ROW } from './configure/wizard/allocationConfigurationRow.component';
import { APPENGINE_LOAD_BALANCER_BASIC_SETTINGS } from './configure/wizard/basicSettings.component';
import { APPENGINE_STAGE_ALLOCATION_CONFIGURATION_ROW } from './configure/wizard/stageAllocationConfigurationRow.component';
import { APPENGINE_LOAD_BALANCER_WIZARD_CTRL } from './configure/wizard/wizard.controller';
import { APPENGINE_LOAD_BALANCER_DETAILS_CTRL } from './details/details.controller';
import { APPENGINE_LOAD_BALANCER_TRANSFORMER } from './transformer';

export const APPENGINE_LOAD_BALANCER_MODULE = 'spinnaker.appengine.loadBalancer.module';

module(APPENGINE_LOAD_BALANCER_MODULE, [
  APPENGINE_ALLOCATION_CONFIGURATION_ROW,
  APPENGINE_LOAD_BALANCER_DETAILS_CTRL,
  APPENGINE_LOAD_BALANCER_ADVANCED_SETTINGS,
  APPENGINE_LOAD_BALANCER_BASIC_SETTINGS,
  APPENGINE_LOAD_BALANCER_TRANSFORMER,
  APPENGINE_LOAD_BALANCER_WIZARD_CTRL,
  APPENGINE_STAGE_ALLOCATION_CONFIGURATION_ROW,
]);
