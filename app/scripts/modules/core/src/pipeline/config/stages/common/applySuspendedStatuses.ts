import { IExecution } from '../../../../domain';

export const applySuspendedStatuses = (execution: IExecution, stageType: string) => {
  const runningStagesOfType = execution.stages.filter(
    (stage) => stage.type === stageType && stage.status === 'RUNNING',
  );

  runningStagesOfType.forEach((stage) => {
    stage.status = 'SUSPENDED';
    stage.tasks.forEach(function (task) {
      task.status = 'SUSPENDED';
    });
  });

  if (runningStagesOfType.length > 0 && execution.status === 'RUNNING') {
    const hasOtherRunningStages = execution.stages.some((stage) => stage.status === 'RUNNING');
    if (!hasOtherRunningStages) {
      execution.status = 'SUSPENDED';
    }
  }
};
