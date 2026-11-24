import { get, last, round, uniq } from 'lodash';

import type { Application, IExecution, IExecutionStage, ITransformer } from '@spinnaker/core';
import { OrchestratedItemTransformer } from '@spinnaker/core';

import {
  CREATE_SERVER_GROUP,
  DEPLOY_CANARY_SERVER_GROUPS,
  KAYENTA_CANARY,
  RUN_CANARY,
  RUN_CANARY_INTERVALS,
  WAIT,
} from './stageTypes';

const stageTypesToAlwaysShow = [KAYENTA_CANARY, CREATE_SERVER_GROUP];

// The stage editor saves score thresholds as strings, so convert before comparing. Missing or blank values become NaN.
const toScore = (value: unknown): number =>
  value === null || value === undefined || value === '' ? NaN : Number(value);

// Orca's KayentaCanaryContext defaults scoreThresholds to these values when the whole object is omitted from the
// canary config. Individual null/blank fields inside a present object are not defaulted.
const DEFAULT_SCORE_THRESHOLDS = { pass: 75, marginal: 50 };

interface IScoreThresholds {
  marginal: number;
  pass: number;
}

const readThresholds = (thresholds: any): IScoreThresholds => ({
  marginal: toScore(get(thresholds, 'marginal', null)),
  pass: toScore(get(thresholds, 'pass', null)),
});

// Mirrors how Orca decides a score has failed: at or below marginal, or below pass.
const scoreFailed = (score: number, { marginal, pass }: IScoreThresholds): boolean =>
  Number.isFinite(score) &&
  ((Number.isFinite(marginal) && score <= marginal) || (Number.isFinite(pass) && score < pass));

// The score shown to the user is rounded to 2 decimals, but Orca compares the raw double against the thresholds, so
// 74.996 fails a pass threshold of 75 even though it displays as 75. The transformer writes the rounded value back into
// the stage context (and re-runs on the same stage objects), so remember the raw value alongside what we rounded it to.
// If the context value no longer matches what we rounded to, the data was replaced and the current value is the raw one.
const rawScores = new WeakMap<object, { raw: number; rounded: number }>();

const roundScore = (stage: object, raw: number): number => {
  const rounded = round(raw, 2);
  rawScores.set(stage, { raw, rounded });
  return rounded;
};

const rawScoreOf = (stage: object, current: number): number => {
  const remembered = rawScores.get(stage);
  return remembered && remembered.rounded === current ? remembered.raw : current;
};

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

        // The interval stage is only a container for the runCanary/wait stages, so it is always folded into
        // the kayentaCanary stage (not just when it succeeds); its own exception, if any, is collected below.
        const intervalStage = intervalStageId
          ? execution.stages.find(({ id, type }) => id === intervalStageId && type === RUN_CANARY_INTERVALS)
          : undefined;
        if (intervalStage) {
          OrchestratedItemTransformer.defineProperties(intervalStage);
          stagesToRenderAsTasks.push(intervalStage);
        }

        const runCanaryStages = syntheticCanaryStages.filter((s) => s.type === RUN_CANARY);
        syntheticCanaryStages.forEach((syntheticStage) => OrchestratedItemTransformer.defineProperties(syntheticStage));
        this.calculateRunCanaryResults(runCanaryStages);
        this.calculateKayentaCanaryResults(stage, syntheticCanaryStages);

        stage.exceptions = [];
        this.addExceptions(
          [stage, ...(intervalStage ? [intervalStage] : []), ...syntheticCanaryStages],
          stage.exceptions,
        );

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
        // Compare the raw score; on a re-transform the context already holds the rounded one.
        const rawScore = rawScoreOf(run, run.context.canaryScore);
        if (run.status === 'SUCCEEDED') {
          if (rawScore >= run.context.scoreThresholds.pass) {
            run.result = 'success';
          }
        } else {
          run.health = 'unhealthy';
        }

        run.context.canaryScore = roundScore(run, rawScore);
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
      kayentaStage.context.overallScore = roundScore(kayentaStage, kayentaStage.context.overallScore);

      // Sometimes when the very first runCanary stage fails due to a low score
      // it never adds its canaryScoreMessage to the kayenta stage.
      if (!kayentaStage.context.canaryScoreMessage) {
        kayentaStage.context.canaryScoreMessage = this.getLastCanaryScoreMessage(runCanaryStages);
      }

      if (!kayentaStage.isCanceled) {
        // A stage that did not succeed only gets a passing badge when it has a score that did not fail, using the same
        // rule that decides whether the score message is shown. Otherwise the badge and the message would disagree.
        const overallScore = rawScoreOf(kayentaStage, toScore(get(kayentaStage, 'context.overallScore', null)));
        const scorePassed =
          Number.isFinite(overallScore) && !scoreFailed(overallScore, this.getEffectiveThresholds(kayentaStage));
        if (kayentaStage.status === 'SUCCEEDED' || scorePassed) {
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
      .map((s) => rawScoreOf(s, s.getValueFor('canaryScore')));
    return last(canaryRunScores);
  }

  private getLastCanaryScoreMessage(runCanaryStages: IExecutionStage[] = []): number {
    const canaryRunMessages = runCanaryStages
      .filter((s) => s.getValueFor('canaryScoreMessage'))
      .map((s) => s.getValueFor('canaryScoreMessage'));
    return last(canaryRunMessages);
  }

  private addExceptions(stages: IExecutionStage[], exceptions: string[]): void {
    const collected: string[] = [];
    let kayentaContributed = false;
    stages.forEach((stage) => {
      const exception = this.getException(stage);
      if (exception) {
        collected.push(exception);
      }
      // Only the kayentaCanary stage's own message is shown, and only when its score actually failed. A stage can
      // have a failed status with a passing score (e.g. a cleanup child failed), and Orca also writes neutral
      // messages (e.g. no thresholds specified), so the stage status is not a reliable signal.
      if (stage.type === KAYENTA_CANARY) {
        const message = get(stage, 'context.canaryScoreMessage', null);
        const overallScore = rawScoreOf(stage, toScore(get(stage, 'context.overallScore', null)));
        if (message && scoreFailed(overallScore, this.getEffectiveThresholds(stage))) {
          collected.push(message);
          kayentaContributed = true;
        }
      }
    });

    // While Orca runs failure cleanup the kayentaCanary stage stays RUNNING, so its overallScore and final message
    // do not exist yet. Until they do, explain the failure with the failed runs' own score messages.
    if (!kayentaContributed) {
      const kayentaStage = stages.find(({ type }) => type === KAYENTA_CANARY);
      const fallbackThresholds = kayentaStage ? this.getEffectiveThresholds(kayentaStage) : readThresholds(null);
      stages
        .filter((stage) => stage.type === RUN_CANARY && stage.isFailed)
        .forEach((run) => {
          const message = get(run, 'context.canaryScoreMessage', null);
          const runThresholds = get(run, 'context.scoreThresholds', null);
          const thresholds = runThresholds ? readThresholds(runThresholds) : fallbackThresholds;
          const score = rawScoreOf(run, toScore(get(run, 'context.canaryScore', null)));
          if (message && scoreFailed(score, thresholds)) {
            collected.push(message);
          }
        });
    }

    const unique = uniq(collected);
    exceptions.splice(0, exceptions.length, ...unique);
  }

  // Orca defaults the thresholds only when the whole object is absent.
  private getEffectiveThresholds(stage: IExecutionStage): IScoreThresholds {
    const thresholds = get(stage, 'context.canaryConfig.scoreThresholds', null);
    return readThresholds(thresholds === null || thresholds === undefined ? DEFAULT_SCORE_THRESHOLDS : thresholds);
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
