'use strict';

angular.module('spinnaker.pipelines.stage.deploy', [
  'spinnaker.pipelines.stage.deploy.details.controller',
  'spinnaker.pipelines.stage',
  'spinnaker.pipelines.stage.core',
  'spinnaker.deploymentStrategy',
  'spinnaker.utils.lodash',
  'spinnaker.serverGroup.read.service',
  'spinnaker.aws.serverGroupCommandBuilder.service',
  'spinnaker.pipelines.stage.deploy.transformer',
])
  .run(function(pipelineConfig, deployStageTransformer) {
    pipelineConfig.registerTransformer(deployStageTransformer);
  });
