import { module } from 'angular';

import { DCOS_LOADBALANCER_DETAILS_DETAILS_CONTROLLER } from './details.controller';

('use strict');

export const DCOS_LOADBALANCER_DETAILS_DETAILS_DCOS_MODULE = 'spinnaker.dcos.loadBalancer.details';
export const name = DCOS_LOADBALANCER_DETAILS_DETAILS_DCOS_MODULE; // for backwards compatibility
module(DCOS_LOADBALANCER_DETAILS_DETAILS_DCOS_MODULE, [DCOS_LOADBALANCER_DETAILS_DETAILS_CONTROLLER]);
