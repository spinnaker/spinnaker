'use strict';

export class WaitForParentTasksTransformer {
  transform(_application, execution) {
    const stagesToInject = [];
    execution.stages
      .filter((stage) => stage.requisiteStageRefIds && stage.requisiteStageRefIds.length > 1)
      .forEach((stage) => {
        const waitStage = execution.stages.find(
          (candidate) =>
            candidate.type === 'waitForRequisiteCompletion' &&
            candidate.context.requisiteIds &&
            candidate.context.requisiteIds.length === stage.requisiteStageRefIds.length &&
            candidate.context.requisiteIds.every((reqId) => stage.requisiteStageRefIds.includes(reqId)),
        );
        if (waitStage) {
          stagesToInject.push({
            parentTasks: execution.stages.filter((parent) => waitStage.context.requisiteIds.includes(parent.refId)),
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
}
