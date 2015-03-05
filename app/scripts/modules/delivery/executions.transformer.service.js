'use strict';

angular.module('deckApp.delivery.executionTransformer.service', [
  'deckApp.orchestratedItem.service',
  'deckApp.utils.lodash',
])
  .factory('executionsTransformer', function(orchestratedItem, _) {

    function transformExecution(execution) {
      var stageSummaries = [];

      execution.stages.forEach(function(stage) {
        if (!stage.syntheticStageOwner) {
          stageSummaries.push({
            name: stage.name,
            id: stage.id,
            masterStage: stage,
            before: [],
            after: []
          });
        }
      });


      execution.stages.forEach(function(stage, index) {
        stage.index = index;
        var owner = stage.syntheticStageOwner;
        var parent = _.find(stageSummaries, { id: stage.parentStageId });
        if (owner === 'STAGE_BEFORE') {
          if (parent) {
            parent.before.push(stage);
          }
        }
        if (owner === 'STAGE_AFTER') {
          if (parent) {
            parent.after.push(stage);
          }
        }
      });

      stageSummaries.forEach(transformStageSummary);
      execution.stageSummaries = stageSummaries;

    }

    function transformStageSummary(summary) {
      summary.stages = summary.before.concat([summary.masterStage]).concat(summary.after).filter(function(stage) {
        return stage.type !== 'initialization';
      });
      if (summary.stages.length) {
        var lastStage = summary.stages[summary.stages.length - 1];
        summary.startTime = summary.stages[0].startTime;
        var currentStage = _(summary.stages).findLast(function(stage) { return !stage.hasNotStarted; }) || lastStage;
        summary.status = currentStage.status;
        summary.endTime = lastStage.endTime;
      }
      orchestratedItem.defineProperties(summary);
    }

    return {
      transformExecution: transformExecution
    };
  });
