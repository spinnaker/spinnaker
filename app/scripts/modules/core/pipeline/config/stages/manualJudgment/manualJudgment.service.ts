import {IPromise, module} from 'angular';

import {EXECUTION_SERVICE, ExecutionService} from 'core/delivery/service/execution.service';
import {IExecution} from 'core/domain/IExecution';
import {IExecutionStage} from 'core/domain/IExecutionStage';

export class ManualJudgmentService {
  constructor(private executionService: ExecutionService) { 'ngInject'; }

  public provideJudgment(execution: IExecution, stage: IExecutionStage, judgmentStatus: string, judgmentInput?: string): IPromise<IExecution> {
      const matcher = (result: IExecution) => {
        const match = result.stages.find((test) => test.id === stage.id);
        return match && match.status !== 'RUNNING';
      };
      return this.executionService.patchExecution(execution.id, stage.id, {judgmentStatus, judgmentInput})
        .then(() => this.executionService.waitUntilExecutionMatches(execution.id, matcher));
  }
}

export let manualJudgmentService: ManualJudgmentService = undefined;
export const MANUAL_JUDGMENT_SERVICE = 'spinnaker.core.pipeline.config.stages.manualJudgment.service';
module(MANUAL_JUDGMENT_SERVICE, [EXECUTION_SERVICE]).service('manualJudgmentService', ManualJudgmentService)
  .run(($injector: any) => manualJudgmentService = <ManualJudgmentService>$injector.get('manualJudgmentService'));
