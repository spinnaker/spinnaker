'use strict';

angular.module('spinnaker.pipelines.stage.manualJudgment')
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Manual Judgment',
      description: 'Waits for user approval before continuing',
      key: 'manualJudgment',
      templateUrl: 'scripts/modules/pipelines/config/stages/manualJudgment/manualJudgmentStage.html',
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/manualJudgment/manualJudgmentExecutionDetails.html',
      executionBarColorProvider: function (stageSummary) {
        if (stageSummary.status === 'RUNNING') {
          return '#F0AD4E';
        }

        return undefined;
      }
    });
  });
