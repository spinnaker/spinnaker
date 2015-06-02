'use strict';

angular.module('spinnaker.pipelines.stage.manualJudgement')
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Manual Judgement',
      description: 'Waits for user approval before continuing',
      key: 'manualJudgement',
      templateUrl: 'scripts/modules/pipelines/config/stages/manualJudgement/manualJudgementStage.html',
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/manualJudgement/manualJudgementExecutionDetails.html',
      executionBarColorProvider: function (stageSummary) {
        if (stageSummary.status === 'RUNNING') {
          return '#F0AD4E';
        }

        return undefined;
      }
    });
  });
