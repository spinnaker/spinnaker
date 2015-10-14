'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.canary', [
  require('./canaryStage.js'),
  require('./canaryExecutionDetails.controller.js'),
  require('./canaryExecutionSummary.controller.js'),
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('../../../../core/deploymentStrategy/deploymentStrategy.module.js'),
  require('../../../../utils/lodash.js'),
  require('../../../../core/serverGroup/serverGroup.read.service.js'),
  require('./canaryDeployment/canaryDeployment.module.js'),
  require('./canaryStage.transformer.js'),
  require('./canaryScore.directive.js'),
  require('./canaryStatus.directive.js'),
  require('../../../../core/account/account.service.js'),
  require('../../../../core/naming/naming.service.js')
])
  .run(function(pipelineConfig, canaryStageTransformer) {
    pipelineConfig.registerTransformer(canaryStageTransformer);
  })
  .name;
