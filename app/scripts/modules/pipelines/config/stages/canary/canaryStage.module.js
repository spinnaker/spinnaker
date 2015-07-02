'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.canary', [
  require('./canaryStage.js'),
  require('./canaryExecutionDetails.controller.js'),
  require('./canaryExecutionSummary.controller.js'),
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('../../../../deploymentStrategy/deploymentStrategy.module.js'),
  require('../../../../authentication/authenticationService.js'),
  require('utils/lodash.js'),
  require('../../../../serverGroups/serverGroup.read.service.js'),
  require('../../../../serverGroups/configure/aws/serverGroupCommandBuilder.service.js'),
  require('./canaryDeployment/canaryDeployment.module.js'),
  require('./canaryStage.transformer.js'),
  require('./canaryScore.directive.js'),
  require('./canaryStatus.directive.js'),
  require('../../../../account/accountService.js'),
  require('../../../../naming/naming.service.js')
])
  .run(function(pipelineConfig, canaryStageTransformer) {
    pipelineConfig.registerTransformer(canaryStageTransformer);
  }).name;
