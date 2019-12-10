'use strict';

const angular = require('angular');

export const KUBERNETES_V1_LOADBALANCER_CONFIGURE_CONFIGURE_KUBERNETES_MODULE =
  'spinnaker.loadBalancer.configure.kubernetes';
export const name = KUBERNETES_V1_LOADBALANCER_CONFIGURE_CONFIGURE_KUBERNETES_MODULE; // for backwards compatibility
angular.module(KUBERNETES_V1_LOADBALANCER_CONFIGURE_CONFIGURE_KUBERNETES_MODULE, [
  require('./wizard/upsert.controller').name,
  require('./wizard/ports.controller').name,
  require('./wizard/advancedSettings.controller').name,
]);
