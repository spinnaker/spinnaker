'use strict';

const angular = require('angular');

export const ORACLE_SERVERGROUP_CONFIGURE_SERVERGROUP_CONFIGURE_MODULE = 'spinnaker.oracle.serverGroup.configure';
export const name = ORACLE_SERVERGROUP_CONFIGURE_SERVERGROUP_CONFIGURE_MODULE; // for backwards compatibility
angular.module(ORACLE_SERVERGROUP_CONFIGURE_SERVERGROUP_CONFIGURE_MODULE, [
  require('./wizard/basicSettings/basicSettings.controller').name,
  require('./wizard/capacity/capacitySelector.component').name,
  require('./serverGroupConfiguration.service').name,
]);
