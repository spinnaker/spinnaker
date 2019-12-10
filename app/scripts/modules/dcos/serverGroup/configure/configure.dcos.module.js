'use strict';

const angular = require('angular');

export const DCOS_SERVERGROUP_CONFIGURE_CONFIGURE_DCOS_MODULE = 'spinnaker.dcos.serverGroup.configure';
export const name = DCOS_SERVERGROUP_CONFIGURE_CONFIGURE_DCOS_MODULE; // for backwards compatibility
angular.module(DCOS_SERVERGROUP_CONFIGURE_CONFIGURE_DCOS_MODULE, [
  require('./configuration.service').name,
  require('./CommandBuilder').name,
  require('./wizard/basicSettings.controller').name,
  require('./wizard/Clone.controller').name,
  require('./wizard/containerSettings.controller').name,
  require('./wizard/environmentVariables.controller').name,
  require('./wizard/healthChecks.controller').name,
  require('./wizard/network.controller').name,
  require('./wizard/volumes.controller').name,
  require('./wizard/optional.controller').name,
]);
