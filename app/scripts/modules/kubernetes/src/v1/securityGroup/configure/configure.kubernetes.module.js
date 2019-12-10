'use strict';

const angular = require('angular');

export const KUBERNETES_V1_SECURITYGROUP_CONFIGURE_CONFIGURE_KUBERNETES_MODULE =
  'spinnaker.securityGroup.configure.kubernetes';
export const name = KUBERNETES_V1_SECURITYGROUP_CONFIGURE_CONFIGURE_KUBERNETES_MODULE; // for backwards compatibility
angular.module(KUBERNETES_V1_SECURITYGROUP_CONFIGURE_CONFIGURE_KUBERNETES_MODULE, [
  require('./wizard/backend.controller').name,
  require('./wizard/rules.controller').name,
  require('./wizard/tls.controller').name,
  require('./wizard/upsert.controller').name,
]);
