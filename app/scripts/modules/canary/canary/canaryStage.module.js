'use strict';

const angular = require('angular');

import { CANARY_SCORE_COMPONENT } from './canaryScore.component';
import { CANARY_SCORES_CONFIG_COMPONENT } from './canaryScores.component';

module.exports = angular
  .module('spinnaker.canary.stage', [
    require('./canaryStage.js').name,
    require('./canaryExecutionDetails.controller.js').name,
    require('./canaryExecutionSummary.controller.js').name,
    require('./canaryDeployment/canaryDeployment.module.js').name,
    require('./canaryStage.transformer.js').name,
    CANARY_SCORE_COMPONENT,
    CANARY_SCORES_CONFIG_COMPONENT,
    require('./canaryStatus.directive.js').name,
  ])
  .run(function(pipelineConfig, canaryStageTransformer) {
    pipelineConfig.registerTransformer(canaryStageTransformer);
  });
