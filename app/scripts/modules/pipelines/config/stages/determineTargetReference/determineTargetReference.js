'use strict';

angular.module('spinnaker.pipelines.stage.determineTargetReference')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      key: 'determineTargetReference',
      synthetic: true,
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/determineTargetReference/determineTargetReferenceDetails.html',
    });
  });

