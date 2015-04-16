'use strict';

angular.module('deckApp.pipelines.stage.canary.canaryDeployment')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      synthetic: true,
      key: 'canaryDeployment',
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/canary/canaryDeployment/canaryDeploymentExecutionDetails.html',
    });
  });
