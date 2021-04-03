import { get, last, round } from 'lodash';

import { Application, IExecution, IExecutionStage, ITransformer, OrchestratedItemTransformer } from '@spinnaker/core';

import { CREATE_SERVER_GROUP, DEPLOY_CANARY_SERVER_GROUPS, KAYENTA_CANARY, RUN_CANARY, WAIT } from './stageTypes';

const stageTypesToAlwaysShow = [KAYENTA_CANARY, CREATE_SERVER_GROUP];

export class KayentaStageTransformer implements ITransformer {
  public transform(_application: Application, execution: IExecution): void {
    const kayentaStages = execution.stages.filter(({ type }) => type === KAYENTA_CANARY);
    if (!kayentaStages.length) {
      return;
    }

    let stagesToRenderAsTasks: IExecutionStage[] = [];

    execution.stages.forEach((stage) => {
      if (stage.type === KAYENTA_CANARY) {
        OrchestratedItemTransformer.defineProperties(stage);

        const intervalStageId: string = stage.context.intervalStageId;
        const syntheticCanaryStages = intervalStageId
          ? execution.stages.filter(
              ({ parentStageId, type }) =>
                parentStageId && parentStageId === intervalStageId && [WAIT, RUN_CANARY].includes(type),
            )
          : [];

        stagesToRenderAsTasks = stagesToRenderAsTasks.concat(syntheticCanaryStages);

        const runCanaryStages = syntheticCanaryStages.filter((s) => s.type === RUN_CANARY);
        syntheticCanaryStages.forEach((syntheticStage) => OrchestratedItemTransformer.defineProperties(syntheticStage));
        this.calculateRunCanaryResults(runCanaryStages);
        this.calculateKayentaCanaryResults(stage, syntheticCanaryStages);

        stage.exceptions = [];
        this.addExceptions([stage, ...syntheticCanaryStages], stage.exceptions);

        // For now, a 'kayentaCanary' stage should only have an 'aggregateCanaryResults' task, which should definitely go last.
        stage.tasks = [...syntheticCanaryStages, ...stage.tasks];
      } else if (stage.type === CREATE_SERVER_GROUP && this.isDescendantOf(stage, kayentaStages, execution)) {
        OrchestratedItemTransformer.defineProperties(stage);
        const parentKayentaStageId = this.isDescendantOf(stage, kayentaStages, execution);
        const locations = stage.context['deploy.server.groups'] && Object.keys(stage.context['deploy.server.groups']);
        stage.name = `Deploy ${stage.context.freeFormDetails}${locations ? ' in ' + locations.join(', ') : ''}`;
        stage.parentStageId = parentKayentaStageId;
      }
    });

    const kayentaStageIds = kayentaStages.map(({ id }) => id);
    const deployCanaryServerGroupsStages = execution.stages.filter(
      ({ parentStageId, type }) => kayentaStageIds.includes(parentStageId) && type === DEPLOY_CANARY_SERVER_GROUPS,
    );

    deployCanaryServerGroupsStages.forEach((deployCanaryStage) => {
      const parentKayentaStage = kayentaStages.find(({ id }) => deployCanaryStage.parentStageId === id);
      if (parentKayentaStage && (deployCanaryStage.outputs.deployedServerGroups || []).length) {
        const [{ controlScope, experimentScope }] = deployCanaryStage.outputs.deployedServerGroups;
        parentKayentaStage.outputs = { ...parentKayentaStage.outputs, controlScope, experimentScope };
      }
    });
    execution.stages = execution.stages.filter(
      (stage) =>
        !stagesToRenderAsTasks.includes(stage) &&
        (!this.isDescendantOf(stage, kayentaStages, execution) ||
          stageTypesToAlwaysShow.includes(stage.type) ||
          stage.status !== 'SUCCEEDED'),
    );
  }

  private isDescendantOf(child: IExecutionStage, ancestors: IExecutionStage[], execution: IExecution) {
    const ancestorIds = ancestors.map(({ id }) => id);
    let node = child;
    while (node && node.parentStageId) {
      const parentNode = execution.stages.find(({ id }) => node.parentStageId === id);
      if (parentNode && ancestorIds.includes(parentNode.id)) {
        return parentNode.id;
      } else {
        node = parentNode;
      }
    }

    return null;
  }

  // Massages each runCanary stage into what the `canaryScore` component expects.
  private calculateRunCanaryResults(runCanaryStages: IExecutionStage[]): void {
    runCanaryStages.forEach((run) => {
      if (typeof run.getValueFor('canaryScore') === 'number') {
        if (run.status === 'SUCCEEDED') {
          if (run.context.canaryScore >= run.context.scoreThresholds.pass) {
            run.result = 'success';
          }
        } else {
          run.health = 'unhealthy';
        }

        run.context.canaryScore = round(run.context.canaryScore, 2);
      }
    });
  }

  // Massages the kayentaCanary stage results into what the `canaryScore` component expects.
  private calculateKayentaCanaryResults(kayentaStage: IExecutionStage, runCanaryStages: IExecutionStage[]): void {
    if (!kayentaStage.isRunning) {
      if (kayentaStage.getValueFor('canaryScores')) {
        // If we made it through the final scheduled canary run, this should
        // be the same as the value returned from `getLastCanaryRunScore`,
        // but this is also how Orca determines the overall score.
        kayentaStage.context.overallScore = last(kayentaStage.getValueFor('canaryScores'));
      } else {
        kayentaStage.context.overallScore = this.getLastCanaryRunScore(runCanaryStages);
      }
      kayentaStage.context.overallScore = round(kayentaStage.context.overallScore, 2);

      // Sometimes when the very first runCanary stage fails due to a low score
      // it never adds its canaryScoreMessage to the kayenta stage.
      if (!kayentaStage.context.canaryScoreMessage) {
        kayentaStage.context.canaryScoreMessage = this.getLastCanaryScoreMessage(runCanaryStages);
      }

      if (!kayentaStage.isCanceled) {
        const overallScore = get(kayentaStage, 'context.overallScore', null);
        const scoreThreshold = get(kayentaStage, 'context.canaryConfig.scoreThresholds.marginal', null);
        if (kayentaStage.status === 'SUCCEEDED' || (overallScore && overallScore > scoreThreshold)) {
          kayentaStage.context.overallResult = 'success';
        } else {
          kayentaStage.context.overallHealth = 'unhealthy';
        }
      }
    }
  }

  private getLastCanaryRunScore(runCanaryStages: IExecutionStage[] = []): number {
    const canaryRunScores = runCanaryStages
      .filter((s) => typeof s.getValueFor('canaryScore') === 'number')
      .map((s) => s.getValueFor('canaryScore'));
    return last(canaryRunScores);
  }

  private getLastCanaryScoreMessage(runCanaryStages: IExecutionStage[] = []): number {
    const canaryRunMessages = runCanaryStages
      .filter((s) => s.getValueFor('canaryScoreMessage'))
      .map((s) => s.getValueFor('canaryScoreMessage'));
    return last(canaryRunMessages);
  }

  private addExceptions(stages: IExecutionStage[], exceptions: string[]): void {
    stages.forEach((stage) => {
      if (this.getException(stage)) {
        exceptions.push(this.getException(stage));
      }
      const overallScore = get(stage, 'context.overallScore', null);
      const scoreThreshold = get(stage, 'context.canaryConfig.scoreThresholds.marginal', null);
      const message = get(stage, 'context.canaryScoreMessage', null);
      if (overallScore && message && overallScore <= scoreThreshold) {
        exceptions.push(message);
      }
    });
  }

  private getException(stage: IExecutionStage): string {
    if (stage && stage.isFailed) {
      if (
        stage.context &&
        stage.context.exception &&
        stage.context.exception.details &&
        stage.context.exception.details.responseBody
      ) {
        return stage.context.exception.details.responseBody;
      } else {
        return stage.failureMessage;
      }
    } else {
      return null;
    }
  }
}
