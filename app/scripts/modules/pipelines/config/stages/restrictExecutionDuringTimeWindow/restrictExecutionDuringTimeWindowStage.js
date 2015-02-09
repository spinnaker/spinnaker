'use strict';

angular.module('deckApp.pipelines.stage.restrictExecutionDuringTimeWindow')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Restrict Execution During',
      description: 'Restricts execution of stage during specified period of time',
      key: 'restrictExecutionDuringTimeWindow',
      templateUrl: 'scripts/modules/pipelines/config/stages/restrictExecutionDuringTimeWindow/restrictExecutionDuringTimeWindowStage.html',
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/restrictExecutionDuringTimeWindow/restrictExecutionDuringTimeWindowExecutionDetails.html',
    });
  });
