import { module } from 'angular';
import { last, round } from 'lodash';

import { Application, IExecution, IExecutionStage, ITransformer, OrchestratedItemTransformer } from '@spinnaker/core';
import { KAYENTA_CANARY, RUN_CANARY, WAIT, CREATE_SERVER_GROUP, DEPLOY_CANARY_SERVER_GROUPS } from './stageTypes';

const stageTypesToAlwaysShow = [KAYENTA_CANARY, CREATE_SERVER_GROUP];

export class KayentaStageTransformer implements ITransformer {
  public transform(_application: Application, execution: IExecution): void {
    const kayentaStage = execution.stages.find(({ type }) => type === KAYENTA_CANARY);
    if (!kayentaStage) {
      return;
    }

    let stagesToRenderAsTasks: IExecutionStage[] = [];

    execution.stages.forEach(stage => {
      if (stage.type === KAYENTA_CANARY) {
        OrchestratedItemTransformer.defineProperties(stage);

        const intervalStageId = stage.context.intervalStageId;
        const syntheticCanaryStages = execution.stages.filter(
          s => s.parentStageId === intervalStageId && [WAIT, RUN_CANARY].includes(s.type),
        );
        stagesToRenderAsTasks = stagesToRenderAsTasks.concat(syntheticCanaryStages);

        stage.exceptions = [];
        this.addExceptions([stage, ...syntheticCanaryStages], stage.exceptions);

        const runCanaryStages = syntheticCanaryStages.filter(s => s.type === RUN_CANARY);
        this.calculateRunCanaryResults(runCanaryStages);
        this.calculateKayentaCanaryResults(stage, syntheticCanaryStages);

        // For now, a 'kayentaCanary' stage should only have an 'aggregateCanaryResults' task, which should definitely go last.
        stage.tasks = [...syntheticCanaryStages, ...stage.tasks];
      } else if (stage.type === CREATE_SERVER_GROUP) {
        OrchestratedItemTransformer.defineProperties(stage);
        const locations = stage.context['deploy.server.groups'] && Object.keys(stage.context['deploy.server.groups']);
        stage.name = `Deploy ${stage.context.freeFormDetails}${locations ? ' in ' + locations.join(', ') : ''}`;
        stage.parentStageId = kayentaStage.id;
      }
    });

    const deployCanaryServerGroupsStage = execution.stages.find(
      ({ parentStageId, type }) => parentStageId === kayentaStage.id && type === DEPLOY_CANARY_SERVER_GROUPS,
    );

    if (deployCanaryServerGroupsStage && (deployCanaryServerGroupsStage.outputs.deployedServerGroups || []).length) {
      const [{ controlScope, experimentScope }] = deployCanaryServerGroupsStage.outputs.deployedServerGroups;
      kayentaStage.outputs = { ...kayentaStage.outputs, controlScope, experimentScope };
    }

    execution.stages = execution.stages.filter(
      stage =>
        !stagesToRenderAsTasks.includes(stage) &&
        (!this.isDescendantOf(stage, kayentaStage, execution) ||
          stageTypesToAlwaysShow.includes(stage.type) ||
          stage.status !== 'SUCCEEDED'),
    );
  }

  private isDescendantOf(child: IExecutionStage, ancestor: IExecutionStage, execution: IExecution) {
    let node = child;
    while (node && node.parentStageId) {
      const parentNode = execution.stages.find(({ id }) => node.parentStageId === id);
      if (parentNode && parentNode.id === ancestor.id) {
        return true;
      } else {
        node = parentNode;
      }
    }

    return false;
  }

  // Massages each runCanary stage into what the `canaryScore` component expects.
  private calculateRunCanaryResults(runCanaryStages: IExecutionStage[]): void {
    runCanaryStages.forEach(run => {
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

      if (!kayentaStage.isCanceled) {
        if (kayentaStage.status === 'SUCCEEDED') {
          kayentaStage.context.overallResult = 'success';
        } else {
          kayentaStage.context.overallHealth = 'unhealthy';
        }
      }
    }
  }

  private getLastCanaryRunScore(runCanaryStages: IExecutionStage[] = []): number {
    const canaryRunScores = runCanaryStages
      .filter(s => typeof s.getValueFor('canaryScore') === 'number')
      .map(s => s.getValueFor('canaryScore'));
    return last(canaryRunScores);
  }

  private addExceptions(stages: IExecutionStage[], exceptions: string[]): void {
    stages.forEach(stage => {
      OrchestratedItemTransformer.defineProperties(stage);
      if (this.getException(stage)) {
        exceptions.push(this.getException(stage));
      }
      if (stage.isFailed && stage.context && stage.context.canaryScoreMessage) {
        exceptions.push(stage.context.canaryScoreMessage);
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

export const KAYENTA_STAGE_TRANSFORMER = 'spinnaker.kayenta.kayentaStageTransformer';
module(KAYENTA_STAGE_TRANSFORMER, []).service('kayentaStageTransformer', KayentaStageTransformer);
