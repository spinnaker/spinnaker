'use strict';

import { module } from 'angular';

import { Registry } from '@spinnaker/core';

export const CANARY_CANARY_CANARYDEPLOYMENT_CANARYDEPLOYMENTSTAGE = 'spinnaker.canary.canaryDeploymentStage';
export const name = CANARY_CANARY_CANARYDEPLOYMENT_CANARYDEPLOYMENTSTAGE; // for backwards compatibility
module(CANARY_CANARY_CANARYDEPLOYMENT_CANARYDEPLOYMENTSTAGE, []).config(function () {
  Registry.pipeline.registerStage({
    synthetic: true,
    key: 'canaryDeployment',
    executionDetailsUrl: require('./canaryDeploymentExecutionDetails.html'),
  });
});
