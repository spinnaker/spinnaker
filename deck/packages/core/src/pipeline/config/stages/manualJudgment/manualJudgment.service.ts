import type { Application } from '../../../../application';
import type { IExecution, IExecutionStage } from '../../../../domain';
import type { ExecutionService } from '../../../service/execution.service';

export class ManualJudgmentService {
  constructor(private executionService: ExecutionService) {}

  public provideJudgment(
    application: Application,
    execution: IExecution,
    stage: IExecutionStage,
    judgmentStatus: string,
    judgmentInput?: string,
  ): PromiseLike<void> {
    const { executionService } = this;
    const matcher = (result: IExecution) => {
      const match = result.stages.find((test) => test.id === stage.id);
      return match && match.status !== 'RUNNING';
    };
    return executionService
      .patchExecution(execution.id, stage.id, { judgmentStatus, judgmentInput })
      .then(() => executionService.waitUntilExecutionMatches(execution.id, matcher))
      .then((updated) => executionService.updateExecution(application, updated));
  }
}
