'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.canary', [
  require('./canaryStage.js'),
  require('./canaryExecutionDetails.controller.js'),
  require('./canaryExecutionSummary.controller.js'),
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('../../../../deploymentStrategy/deploymentStrategy.module.js'),
  require('../../../../utils/lodash.js'),
  require('../../../../serverGroup/serverGroup.read.service.js'),
  require('./canaryDeployment/canaryDeployment.module.js'),
  require('./canaryStage.transformer.js'),
  require('./canaryScore.directive.js'),
  require('./canaryStatus.directive.js'),
  require('../../../../account/account.service.js'),
  require('../../../../naming/naming.service.js')
])
  .run(function(pipelineConfig, canaryStageTransformer) {
    pipelineConfig.registerTransformer(canaryStageTransformer);
  })
  .name;
