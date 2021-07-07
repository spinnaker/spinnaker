'use strict';

import { module } from 'angular';

export const DCOS_LOADBALANCER_CONFIGURE_WIZARD_PORTS_CONTROLLER = 'spinnaker.dcos.loadBalancer.configure.ports';
export const name = DCOS_LOADBALANCER_CONFIGURE_WIZARD_PORTS_CONTROLLER; // for backwards compatibility
module(DCOS_LOADBALANCER_CONFIGURE_WIZARD_PORTS_CONTROLLER, []).controller(
  'dcosLoadBalancerPortsController',
  function () {
    this.protocols = ['tcp', 'udp'];
    this.minPort = 10000;
    this.maxPort = 65535;
  },
);
