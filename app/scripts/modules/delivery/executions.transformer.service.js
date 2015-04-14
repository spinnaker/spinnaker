'use strict';

angular.module('deckApp.delivery.executionTransformer.service', [
  'deckApp.orchestratedItem.service',
  'deckApp.utils.lodash',
])
  .factory('executionsTransformer', function(orchestratedItem, _) {

    function transformExecution(execution) {
      var stageSummaries = [];

      execution.stages.forEach(function(stage, index) {
        stage.before = stage.before || [];
        stage.after = stage.after || [];
        stage.index = index;
        orchestratedItem.defineProperties(stage);
        if (stage.tasks && stage.tasks.length) {
          stage.tasks.forEach(orchestratedItem.defineProperties);
        }
      });

      execution.stages.forEach(function(stage) {
        var owner = stage.syntheticStageOwner;
        var parent = _.find(execution.stages, { id: stage.parentStageId });
        if (parent) {
          if (owner === 'STAGE_BEFORE') {
            parent.before.push(stage);
          }
          if (owner === 'STAGE_AFTER') {
            parent.after.push(stage);
          }
        }
      });

      execution.stages.forEach(function(stage) {
        if (!stage.syntheticStageOwner) {
          stageSummaries.push({
            name: stage.name,
            id: stage.id,
            masterStage: stage,
            before: stage.before,
            after: stage.after,
            status: stage.status
          });
        }
      });

      orchestratedItem.defineProperties(execution);

      stageSummaries.forEach(transformStageSummary);
      execution.stageSummaries = stageSummaries;
      execution.currentStages = getCurrentStages(execution);

    }

    function getCurrentStages(execution) {
      var currentStages = execution.stageSummaries.filter(function(stage) {
        return stage.isRunning;
      });
      // if there are no running stages, find the first enqueued stage
      if (!currentStages.length) {
        var enqueued = execution.stageSummaries.filter(function(stage) {
          return stage.hasNotStarted;
        });
        if (enqueued && enqueued.length) {
          currentStages = [enqueued[0]];
        }
      }
      return currentStages;
    }

    function transformStage(stage) {
      var stages = stage.before.concat([stage.masterStage || stage]).concat(stage.after).filter(function(stage) {
        return stage.type !== 'initialization' && stage.initializationStage !== true;
      });

      if (!stages.length) {
        return;
      } else {
        stage.type = stages[0].type;
      }
      var lastStage = stages[stages.length - 1];
      stage.startTime = stages[0].startTime;

      var lastNotStartedStage = _(stages).findLast(
          function(childStage) {
            return !childStage.hasNotStarted;
          }
        );

      var lastFailedStage = _(stages).findLast(
        function(childStage) {
          return childStage.isFailed;
        }
      );

      var currentStage = lastFailedStage || lastNotStartedStage || lastStage;
      stage.status = currentStage.status;
      stage.endTime = currentStage.endTime;
      stage.stages = stages;

    }

    function transformStageSummary(summary) {
      summary.stages = summary.before.concat([summary.masterStage]).concat(summary.after).filter(function(stage) {
        return stage.type !== 'initialization' && stage.initializationStage !== true;
      });
      summary.stages.forEach(transformStage);
      transformStage(summary);
      orchestratedItem.defineProperties(summary);
    }

    return {
      transformExecution: transformExecution
    };
  });
