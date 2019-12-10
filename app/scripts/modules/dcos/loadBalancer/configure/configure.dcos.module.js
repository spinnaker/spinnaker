'use strict';

const angular = require('angular');

export const DCOS_LOADBALANCER_CONFIGURE_CONFIGURE_DCOS_MODULE = 'spinnaker.dcos.loadBalancer.configure';
export const name = DCOS_LOADBALANCER_CONFIGURE_CONFIGURE_DCOS_MODULE; // for backwards compatibility
angular.module(DCOS_LOADBALANCER_CONFIGURE_CONFIGURE_DCOS_MODULE, [
  require('./wizard/upsert.controller').name,
  require('./wizard/resources.controller').name,
  require('./wizard/ports.controller').name,
]);
