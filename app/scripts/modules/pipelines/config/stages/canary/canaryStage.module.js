'use strict';

angular.module('deckApp.pipelines.stage.canary', [
  'deckApp.pipelines.stage.canary.details.controller',
  'deckApp.pipelines.stage',
  'deckApp.pipelines.stage.core',
  'deckApp.deploymentStrategy',
  'deckApp.utils.lodash',
  'deckApp.serverGroup.read.service',
  'deckApp.aws.serverGroupCommandBuilder.service',
  'deckApp.pipelines.stage.canary.canaryDeployment',
  'deckApp.pipelines.stage.canary.transformer',
])
  .run(function(pipelineConfig, canaryStageTransformer) {
    pipelineConfig.registerTransformer(canaryStageTransformer);
  });
