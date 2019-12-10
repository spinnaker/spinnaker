'use strict';

const angular = require('angular');

export const KUBERNETES_V1_SERVERGROUP_CONFIGURE_CONFIGURE_KUBERNETES_MODULE =
  'spinnaker.serverGroup.configure.kubernetes';
export const name = KUBERNETES_V1_SERVERGROUP_CONFIGURE_CONFIGURE_KUBERNETES_MODULE; // for backwards compatibility
angular.module(KUBERNETES_V1_SERVERGROUP_CONFIGURE_CONFIGURE_KUBERNETES_MODULE, [
  require('./configuration.service').name,
  require('./CommandBuilder').name,
  require('./wizard/BasicSettings.controller').name,
  require('./wizard/advancedSettings.controller').name,
  require('./wizard/Clone.controller').name,
  require('./wizard/volumes.controller').name,
  require('./wizard/deployment.controller').name,
]);
