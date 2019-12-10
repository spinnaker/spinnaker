'use strict';

const angular = require('angular');

export const CANARY_CANARY_CANARYDEPLOYMENT_CANARYDEPLOYMENT_MODULE = 'spinnaker.canary.canaryDeployment';
export const name = CANARY_CANARY_CANARYDEPLOYMENT_CANARYDEPLOYMENT_MODULE; // for backwards compatibility
angular.module(CANARY_CANARY_CANARYDEPLOYMENT_CANARYDEPLOYMENT_MODULE, [
  require('./canaryDeploymentStage').name,
  require('./canaryDeploymentExecutionDetails.controller').name,
]);
