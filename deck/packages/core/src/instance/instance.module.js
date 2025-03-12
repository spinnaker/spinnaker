'use strict';

import { module } from 'angular';

import { CORE_INSTANCE_DETAILS_CONSOLE_CONSOLEOUTPUTLINK_COMPONENT } from './details/console/consoleOutputLink.component';
import { CORE_INSTANCE_DETAILS_INSTANCEACTIONS_COMPONENT } from './details/instanceActions.component';
import { CORE_INSTANCE_DETAILS_HEADER_COMPONENT } from './details/instanceDetailsHeader.component';
import { CORE_INSTANCE_DETAILS_INSTANCEINSIGHTS_COMPONENT } from './details/instanceInsights.component';
import { CORE_INSTANCE_DETAILS_INSTANCELINKS_COMPONENT } from './details/instanceLinks.component';
import { CORE_INSTANCE_DETAILS_MULTIPLEINSTANCES_CONTROLLER } from './details/multipleInstances.controller';
import { INSTANCE_STATES } from './instance.states';
import './instanceSearchResultType';
import { CORE_INSTANCE_LOADBALANCER_INSTANCELOADBALANCERHEALTH_DIRECTIVE } from './loadBalancer/instanceLoadBalancerHealth.directive';

import './instanceSelection.less';

export const CORE_INSTANCE_INSTANCE_MODULE = 'spinnaker.core.instance';
export const name = CORE_INSTANCE_INSTANCE_MODULE; // for backwards compatibility
module(CORE_INSTANCE_INSTANCE_MODULE, [
  CORE_INSTANCE_DETAILS_CONSOLE_CONSOLEOUTPUTLINK_COMPONENT,
  CORE_INSTANCE_LOADBALANCER_INSTANCELOADBALANCERHEALTH_DIRECTIVE,
  CORE_INSTANCE_DETAILS_MULTIPLEINSTANCES_CONTROLLER,
  CORE_INSTANCE_DETAILS_INSTANCELINKS_COMPONENT,
  CORE_INSTANCE_DETAILS_INSTANCEACTIONS_COMPONENT,
  CORE_INSTANCE_DETAILS_INSTANCEINSIGHTS_COMPONENT,
  CORE_INSTANCE_DETAILS_HEADER_COMPONENT,
  INSTANCE_STATES,
]);
