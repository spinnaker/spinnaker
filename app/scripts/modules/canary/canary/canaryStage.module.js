'use strict';

const angular = require('angular');

import { Registry } from '@spinnaker/core';

import { CANARY_SCORE_COMPONENT } from './canaryScore.component';
import { CANARY_SCORES_CONFIG_COMPONENT } from './canaryScores.component';

module.exports = angular
  .module('spinnaker.canary.stage', [
    require('./canaryStage').name,
    require('./canaryExecutionDetails.controller').name,
    require('./canaryExecutionSummary.controller').name,
    require('./canaryDeployment/canaryDeployment.module').name,
    require('./canaryStage.transformer').name,
    CANARY_SCORE_COMPONENT,
    CANARY_SCORES_CONFIG_COMPONENT,
    require('./canaryStatus.directive').name,
  ])
  .run([
    'canaryStageTransformer',
    function(canaryStageTransformer) {
      Registry.pipeline.registerTransformer(canaryStageTransformer);
    },
  ]);
