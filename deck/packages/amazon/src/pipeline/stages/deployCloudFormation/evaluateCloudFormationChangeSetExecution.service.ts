import type { ExecutionService } from '@spinnaker/core';
import type { IExecution, IExecutionStage } from '@spinnaker/core';
import type { Application } from '@spinnaker/core';

export class EvaluateCloudFormationChangeSetExecutionService {
  constructor(private executionService: ExecutionService) {}

  public evaluateExecution(
    application: Application,
    execution: IExecution,
    stage: IExecutionStage,
    changeSetExecutionChoice: string,
  ): PromiseLike<void> {
    const matcher = (result: IExecution) => {
      const match = result.stages.find((test: { id: any }) => test.id === stage.id);
      return match && match.status !== 'RUNNING';
    };
    return this.executionService
      .patchExecution(execution.id, stage.id, { changeSetExecutionChoice })
      .then(() => this.executionService.waitUntilExecutionMatches(execution.id, matcher))
      .then((updated: any) => this.executionService.updateExecution(application, updated));
  }
}
