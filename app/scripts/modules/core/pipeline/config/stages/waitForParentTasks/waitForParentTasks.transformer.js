'use strict';
let angular = require('angular');
module.exports =  angular.module('spinnaker.core.pipeline.stage.waitForParentTasks.transformer', [])
  .service('waitForParentTasksTransformer', function() {

    // injects wait for parent tasks stage
    function injectWaitForParentStages(execution) {
      /*
      * Every time you see one, look at its requisite ids, then set it as the child of any that match it
      * */
      let stagesToInject = [];
      execution.stages
        .filter((stage) => stage.requisiteStageRefIds && stage.requisiteStageRefIds.length > 1)
        .forEach(function (stage) {
          let waitStages = execution.stages.filter((candidate) => candidate.type === 'waitForRequisiteCompletion' &&
              candidate.context.requisiteIds &&
              candidate.context.requisiteIds.length === stage.requisiteStageRefIds.length &&
              candidate.context.requisiteIds.every((reqId) => stage.requisiteStageRefIds.indexOf(reqId) > -1)
          );
          if (waitStages.length) {
            let waitStage = waitStages[0],
                parentStages = execution.stages
                  .filter((parent) => waitStage.context.requisiteIds.indexOf(parent.refId) > -1);
            stagesToInject.push({
              parentTasks: parentStages,
              syntheticStageOwner: 'STAGE_BEFORE',
              id: [waitStage.id, stage.refId].join(':'),
              context: waitStage.context,
              parentStageId: stage.id,
              name: 'Wait for Parent Stages',
              type: waitStage.type,
              startTime: waitStage.startTime,
              endTime: waitStage.endTime,
              status: waitStage.status
            });
          }
        });
      execution.stages = execution.stages.concat(stagesToInject);
    }
    this.transform = function(application, execution) {
      injectWaitForParentStages(execution);
    };
  });
