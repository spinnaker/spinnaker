'use strict';

angular.module('spinnaker.pipelines.stage.wait')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Wait',
      description: 'Waits a specified period of time',
      key: 'wait',
      templateUrl: 'scripts/modules/pipelines/config/stages/wait/waitStage.html',
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/wait/waitExecutionDetails.html',
    });
  });
