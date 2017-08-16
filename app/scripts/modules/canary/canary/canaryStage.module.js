'use strict';

const angular = require('angular');

import { ACCOUNT_SERVICE, NAMING_SERVICE } from '@spinnaker/core';

import { CANARY_SCORE_COMPONENT } from './canaryScore.component';
import { CANARY_SCORES_CONFIG_COMPONENT } from './canaryScores.component';

module.exports = angular.module('spinnaker.canary.stage', [
  require('./canaryStage.js'),
  require('./canaryExecutionDetails.controller.js'),
  require('./canaryExecutionSummary.controller.js'),
  require('./canaryDeployment/canaryDeployment.module.js'),
  require('./canaryStage.transformer.js'),
  CANARY_SCORE_COMPONENT,
  CANARY_SCORES_CONFIG_COMPONENT,
  require('./canaryStatus.directive.js'),
  ACCOUNT_SERVICE,
  NAMING_SERVICE,
])
  .run(function(pipelineConfig, canaryStageTransformer) {
    pipelineConfig.registerTransformer(canaryStageTransformer);
  });
