'use strict';

angular.module('spinnaker.pipelines.stage.canary', [
  'spinnaker.pipelines.stage.canary.details.controller',
  'spinnaker.pipelines.stage.canary.summary.controller',
  'spinnaker.pipelines.stage',
  'spinnaker.pipelines.stage.core',
  'spinnaker.deploymentStrategy',
  'spinnaker.authentication.service',
  'spinnaker.utils.lodash',
  'spinnaker.serverGroup.read.service',
  'spinnaker.aws.serverGroupCommandBuilder.service',
  'spinnaker.pipelines.stage.canary.canaryDeployment',
  'spinnaker.pipelines.stage.canary.transformer',
  'spinnaker.pipelines.stages.canary.score.directive',
  'spinnaker.pipelines.stages.canary.status.directive',
  'spinnaker.account.service',
  'spinnaker.naming'
])
  .run(function(pipelineConfig, canaryStageTransformer) {
    pipelineConfig.registerTransformer(canaryStageTransformer);
  });
