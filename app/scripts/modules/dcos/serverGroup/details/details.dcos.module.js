import { DCOS_SERVERGROUP_DETAILS_DETAILS_CONTROLLER } from './details.controller';
import { DCOS_SERVERGROUP_DETAILS_RESIZE_RESIZE_CONTROLLER } from './resize/resize.controller';
('use strict');

const angular = require('angular');

export const DCOS_SERVERGROUP_DETAILS_DETAILS_DCOS_MODULE = 'spinnaker.dcos.serverGroup.details';
export const name = DCOS_SERVERGROUP_DETAILS_DETAILS_DCOS_MODULE; // for backwards compatibility
angular.module(DCOS_SERVERGROUP_DETAILS_DETAILS_DCOS_MODULE, [
  DCOS_SERVERGROUP_DETAILS_DETAILS_CONTROLLER,
  DCOS_SERVERGROUP_DETAILS_RESIZE_RESIZE_CONTROLLER,
]);
