import { IExecution } from '@spinnaker/core';

import { IMetricSetPair, ICanaryJudgeStage } from 'kayenta/domain/index';
import { ICanaryRunService } from './canaryRun.service';
import { CANARY_JUDGE } from './canaryRunStages';

const canaryRuns: IExecution[] =
  require.context('kayenta/scratch/canaryRun/', false, /^\.\/canary_run_\d+.json$/)
    .keys()
    .map(runPath => require(`kayenta/scratch/canaryRun/${runPath.slice(2)}`));

/*
* For local development only. Enabled if LIVE_CALLS env variable is false.
* */
export class LocalCanaryRunService implements ICanaryRunService {
  private canaryRuns: IExecution[];

  constructor(runs: IExecution[]) {
    this.canaryRuns = runs;
  }

  public getCanaryRunsForConfig(configName: string): Promise<IExecution[]> {
    const runs = this.canaryRuns.filter(r => {
      const judgeStage = this.getCanaryJudgeStage(r);
      return judgeStage && judgeStage.context.canaryConfigId === configName;
    });
    return Promise.all(runs);
  }

  public getCanaryRun(configName: string, runId: string): Promise<IExecution> {
    return this.getCanaryRunsForConfig(configName).then(runs => {
      const run = runs.find(r => r.id === runId);
      if (run) {
        return run;
      } else {
        throw new Error(`Canary run ${configName}:${runId} not found`);
      }
    });
  }

  public getMetricSetPair(configName: string, runId: string, pairId: string): Promise<IMetricSetPair> {
    return this.getCanaryRun(configName, runId).then(run => {
      const judgeStage = this.getCanaryJudgeStage(run);
      const metricSetPairList = require(`metric_set_pair_list_${judgeStage.context.canaryJudgeResultId}.json`) as IMetricSetPair[];
      return metricSetPairList
        ? metricSetPairList.find(pair => pair.id === pairId)
        : null;
    });
  }

  private getCanaryJudgeStage(canaryRun: IExecution): ICanaryJudgeStage {
    return canaryRun.stages.find(s => s.type === CANARY_JUDGE) as ICanaryJudgeStage;
  }
}

export const localCanaryRunService = new LocalCanaryRunService(canaryRuns);
