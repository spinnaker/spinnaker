import { module } from 'angular';
import type { ExecutionService } from '@spinnaker/core';
import { EXECUTION_SERVICE } from '@spinnaker/core';
import type { IExecution, IExecutionStage } from '@spinnaker/core';
import type { Application } from '@spinnaker/core';

export class EvaluateCloudFormationChangeSetExecutionService {
  public static $inject = ['executionService'];
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

export const AWS_EVALUATE_CLOUD_FORMATION_CHANGE_SET_EXECUTION_SERVICE =
  'spinnaker.amazon.deployCloudFormation.service';
module(AWS_EVALUATE_CLOUD_FORMATION_CHANGE_SET_EXECUTION_SERVICE, [EXECUTION_SERVICE]).service(
  'evaluateCloudFormationChangeSetExecutionService',
  EvaluateCloudFormationChangeSetExecutionService,
);
