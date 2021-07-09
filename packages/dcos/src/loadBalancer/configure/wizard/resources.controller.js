'use strict';

import { module } from 'angular';

export const DCOS_LOADBALANCER_CONFIGURE_WIZARD_RESOURCES_CONTROLLER =
  'spinnaker.dcos.loadBalancer.configure.resources';
export const name = DCOS_LOADBALANCER_CONFIGURE_WIZARD_RESOURCES_CONTROLLER; // for backwards compatibility
module(DCOS_LOADBALANCER_CONFIGURE_WIZARD_RESOURCES_CONTROLLER, []).controller(
  'dcosLoadBalancerResourcesController',
  function () {
    this.minCpus = 0.01;
  },
);
