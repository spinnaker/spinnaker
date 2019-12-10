'use strict';

const angular = require('angular');

import { Registry } from '@spinnaker/core';

import { CANARY_SCORE_COMPONENT } from '../canary/canaryScore.component';

export const CANARY_ACATASK_ACATASKSTAGE_MODULE = 'spinnaker.canary.genericCanary';
export const name = CANARY_ACATASK_ACATASKSTAGE_MODULE; // for backwards compatibility
angular
  .module(CANARY_ACATASK_ACATASKSTAGE_MODULE, [
    require('./acaTaskStage').name,
    require('./acaTaskExecutionDetails.controller').name,
    require('./acaTaskStage.transformer').name,
    CANARY_SCORE_COMPONENT,
    require('../canary/canaryStatus.directive').name,
  ])
  .run([
    'acaTaskTransformer',
    function(acaTaskTransformer) {
      Registry.pipeline.registerTransformer(acaTaskTransformer);
    },
  ]);
