'use strict';

const angular = require('angular');

export const DCOS_SERVERGROUP_DETAILS_DETAILS_DCOS_MODULE = 'spinnaker.dcos.serverGroup.details';
export const name = DCOS_SERVERGROUP_DETAILS_DETAILS_DCOS_MODULE; // for backwards compatibility
angular.module(DCOS_SERVERGROUP_DETAILS_DETAILS_DCOS_MODULE, [
  require('./details.controller').name,
  require('./resize/resize.controller').name,
]);
