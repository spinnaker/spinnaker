import { DCOS_INSTANCE_DETAILS_DETAILS_CONTROLLER } from './details.controller';
('use strict');

const angular = require('angular');

export const DCOS_INSTANCE_DETAILS_DETAILS_DCOS_MODULE = 'spinnaker.dcos.instance.details';
export const name = DCOS_INSTANCE_DETAILS_DETAILS_DCOS_MODULE; // for backwards compatibility
angular.module(DCOS_INSTANCE_DETAILS_DETAILS_DCOS_MODULE, [DCOS_INSTANCE_DETAILS_DETAILS_CONTROLLER]);
