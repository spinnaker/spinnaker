import { module } from 'angular';
import { last, round } from 'lodash';

import {
  Application, IExecution, IExecutionStage, ITransformer,
  OrchestratedItemTransformer
} from '@spinnaker/core';
import { KAYENTA_CANARY, RUN_CANARY } from './stageTypes';

export class KayentaStageTransformer implements ITransformer {

  public transform(_application: Application, execution: IExecution): void {
    let stagesToRenderAsTasks: IExecutionStage[] = [];
    execution.stages.forEach(stage => {
      if (stage.type === KAYENTA_CANARY) {
        OrchestratedItemTransformer.defineProperties(stage);

        const syntheticCanaryStages = execution.stages.filter(s => s.parentStageId === stage.id);
        stagesToRenderAsTasks = stagesToRenderAsTasks.concat(syntheticCanaryStages);

        stage.exceptions = [];
        this.addExceptions([stage, ...syntheticCanaryStages], stage.exceptions);

        const runCanaryStages = syntheticCanaryStages.filter(s => s.type === RUN_CANARY);
        this.calculateRunCanaryResults(runCanaryStages);
        this.calculateKayentaCanaryResults(stage, syntheticCanaryStages);

        // For now, a 'kayentaCanary' stage should only have an 'aggregateCanaryResults' task, which should definitely go last.
        stage.tasks = [...syntheticCanaryStages, ...stage.tasks];
      }
    });

    execution.stages = execution.stages.filter(stage => !stagesToRenderAsTasks.includes(stage));
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
        kayentaStage.overallScore = last(kayentaStage.getValueFor('canaryScores'));
      } else {
        kayentaStage.overallScore = this.getLastCanaryRunScore(runCanaryStages);
      }
      kayentaStage.overallScore = round(kayentaStage.overallScore, 2);

      if (!kayentaStage.isCanceled) {
        if (kayentaStage.status === 'SUCCEEDED') {
          kayentaStage.overallResult = 'success';
        } else {
          kayentaStage.overallHealth = 'unhealthy';
        }
      }
    }
  }

  private getLastCanaryRunScore(runCanaryStages: IExecutionStage[] = []): number {
    const canaryRunScores = runCanaryStages.filter(s => typeof s.getValueFor('canaryScore') === 'number').map(s => s.getValueFor('canaryScore'));
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
    return stage && stage.isFailed ? stage.failureMessage : null;
  }
}

export const KAYENTA_STAGE_TRANSFORMER = 'spinnaker.kayenta.kayentaStageTransformer';
module(KAYENTA_STAGE_TRANSFORMER, [])
  .service('kayentaStageTransformer', KayentaStageTransformer);
