'use strict';
import { module } from 'angular';

export const CORE_PIPELINE_CONFIG_STAGES_WAITFORPARENTTASKS_WAITFORPARENTTASKS_TRANSFORMER =
  'spinnaker.core.pipeline.stage.waitForParentTasks.transformer';
export const name = CORE_PIPELINE_CONFIG_STAGES_WAITFORPARENTTASKS_WAITFORPARENTTASKS_TRANSFORMER; // for backwards compatibility
module(CORE_PIPELINE_CONFIG_STAGES_WAITFORPARENTTASKS_WAITFORPARENTTASKS_TRANSFORMER, []).service(
  'waitForParentTasksTransformer',
  function () {
    // injects wait for parent tasks stage
    function injectWaitForParentStages(execution) {
      /*
       * Every time you see one, look at its requisite ids, then set it as the child of any that match it
       * */
      const stagesToInject = [];
      execution.stages
        .filter((stage) => stage.requisiteStageRefIds && stage.requisiteStageRefIds.length > 1)
        .forEach(function (stage) {
          const waitStages = execution.stages.filter(
            (candidate) =>
              candidate.type === 'waitForRequisiteCompletion' &&
              candidate.context.requisiteIds &&
              candidate.context.requisiteIds.length === stage.requisiteStageRefIds.length &&
              candidate.context.requisiteIds.every((reqId) => stage.requisiteStageRefIds.includes(reqId)),
          );
          if (waitStages.length) {
            const waitStage = waitStages[0];
            const parentStages = execution.stages.filter((parent) =>
              waitStage.context.requisiteIds.includes(parent.refId),
            );
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
              status: waitStage.status,
            });
          }
        });
      execution.stages = execution.stages.concat(stagesToInject);
    }
    this.transform = function (application, execution) {
      injectWaitForParentStages(execution);
    };
  },
);
