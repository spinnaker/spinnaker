'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.deploy', [
  require('./deployExecutionDetails.controller.js'),
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('../../../../deploymentStrategy/deploymentStrategy.module.js'),
  require('utils/lodash.js'),
  require('../../../../serverGroups/serverGroup.read.service.js'),
  require('../../../../serverGroups/configure/aws/serverGroupCommandBuilder.service.js'),
  require('./deployStage.transformer.js'),
])
  .run(function(pipelineConfig, deployStageTransformer) {
    pipelineConfig.registerTransformer(deployStageTransformer);
  }).name;
