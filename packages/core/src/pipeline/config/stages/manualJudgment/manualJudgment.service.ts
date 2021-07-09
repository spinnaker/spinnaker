import { module } from 'angular';
import { Application } from '../../../../application';
import { IExecution, IExecutionStage } from '../../../../domain';

import { EXECUTION_SERVICE, ExecutionService } from '../../../service/execution.service';

export class ManualJudgmentService {
  public static $inject = ['executionService'];
  constructor(private executionService: ExecutionService) {}

  public provideJudgment(
    application: Application,
    execution: IExecution,
    stage: IExecutionStage,
    judgmentStatus: string,
    judgmentInput?: string,
  ): PromiseLike<void> {
    const matcher = (result: IExecution) => {
      const match = result.stages.find((test) => test.id === stage.id);
      return match && match.status !== 'RUNNING';
    };
    return this.executionService
      .patchExecution(execution.id, stage.id, { judgmentStatus, judgmentInput })
      .then(() => this.executionService.waitUntilExecutionMatches(execution.id, matcher))
      .then((updated) => this.executionService.updateExecution(application, updated));
  }
}

export const MANUAL_JUDGMENT_SERVICE = 'spinnaker.core.pipeline.config.stages.manualJudgment.service';
module(MANUAL_JUDGMENT_SERVICE, [EXECUTION_SERVICE]).service('manualJudgmentService', ManualJudgmentService);
