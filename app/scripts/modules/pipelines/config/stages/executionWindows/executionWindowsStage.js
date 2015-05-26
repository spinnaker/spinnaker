'use strict';

angular.module('spinnaker.pipelines.stage.executionWindows')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Restrict Execution During',
      synthetic: true,
      description: 'Restricts execution of stage during specified period of time',
      key: 'restrictExecutionDuringTimeWindow',
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/executionWindows/executionWindowsDetails.html',
    });
  });
