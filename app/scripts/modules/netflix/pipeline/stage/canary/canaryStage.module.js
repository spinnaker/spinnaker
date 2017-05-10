'use strict';

let angular = require('angular');

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {CANARY_SCORE_COMPONENT} from './canaryScore.component';
import {NAMING_SERVICE} from 'core/naming/naming.service';

module.exports = angular.module('spinnaker.netflix.pipeline.stage.canary', [
  require('./canaryStage.js'),
  require('./canaryExecutionDetails.controller.js'),
  require('./canaryExecutionSummary.controller.js'),
  require('./canaryDeployment/canaryDeployment.module.js'),
  require('./canaryStage.transformer.js'),
  CANARY_SCORE_COMPONENT,
  require('./canaryStatus.directive.js'),
  ACCOUNT_SERVICE,
  NAMING_SERVICE,
])
  .run(function(pipelineConfig, canaryStageTransformer) {
    pipelineConfig.registerTransformer(canaryStageTransformer);
  });
