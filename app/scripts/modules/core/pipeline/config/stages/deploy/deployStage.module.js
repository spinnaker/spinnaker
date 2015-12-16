'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.deploy', [
  require('../../../../utils/lodash.js'),
  require('./deployStage.js'),
  require('./deployStage.transformer.js'),
  require('./deployExecutionDetails.controller.js'),
  require('./clusterName.filter.js'),
  require('../core/stage.core.module.js'),
  require('../../../../deploymentStrategy/deploymentStrategy.module.js'),
  require('../../../../account/providerToggles.directive.js'),
])
  .run(function(pipelineConfig, deployStageTransformer) {
    pipelineConfig.registerTransformer(deployStageTransformer);
  });
