'use strict';

let angular = require('angular');
import {ACCOUNT_SERVICE} from 'core/account/account.service';

module.exports = angular.module('spinnaker.netflix.pipeline.stage.canary', [
  require('./canaryStage.js'),
  require('./canaryExecutionDetails.controller.js'),
  require('./canaryExecutionSummary.controller.js'),
  require('core/deploymentStrategy/deploymentStrategy.module.js'),
  require('core/serverGroup/serverGroup.read.service.js'),
  require('./canaryDeployment/canaryDeployment.module.js'),
  require('./canaryStage.transformer.js'),
  require('./canaryScore.directive.js'),
  require('./canaryStatus.directive.js'),
  ACCOUNT_SERVICE,
  require('core/naming/naming.service.js')
])
  .run(function(pipelineConfig, canaryStageTransformer) {
    pipelineConfig.registerTransformer(canaryStageTransformer);
  });
