import { IExecution } from '@spinnaker/core';

import { IMetricSetPair, ICanaryJudgeStage } from 'kayenta/domain/index';
import { CANARY_JUDGE } from './canaryRunStages';

// This loads the JSON into the build - not fetched dynamically.
const canaryRuns: IExecution[] =
  require.context('kayenta/scratch/canaryRun/', false, /^\.\/canary_run_\d+\.json$/)
    .keys()
    .map(runPath => require(`kayenta/scratch/canaryRun/${runPath.slice(2)}`));

const metricSetPairLists: {runId: string, list: IMetricSetPair[]}[] =
  require.context('kayenta/scratch/canaryRun/', false, /^\.\/metric_set_pair_list_.+\.json$/)
    .keys()
    .map(listPath => {
      const [, runId] = /^\.\/metric_set_pair_list_(.+)\.json$/.exec(listPath);
      return {
        runId,
        list: require(`kayenta/scratch/canaryRun/${listPath.slice(2)}`),
      };
    });

/*
* For local development only. Enabled if LIVE_CALLS env variable is false.
* TODO(dpeach): either update to match Kayenta interface or remove.
* */
export class LocalCanaryRunService {
  private canaryRuns: IExecution[];
  private metricSetPairLists: {runId: string, list: IMetricSetPair[]}[];

  constructor(runs: IExecution[], lists: {runId: string, list: IMetricSetPair[]}[]) {
    this.canaryRuns = runs;
    this.metricSetPairLists = lists;
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

  public getMetricSetPair(_configName: string, runId: string, pairId: string): Promise<IMetricSetPair> {
    const metricSetPairList = this.metricSetPairLists.find(l => l.runId === runId);
    return metricSetPairList
      ? Promise.resolve(metricSetPairList.list.find(pair => pair.id === pairId))
      : Promise.resolve(null);
  }

  private getCanaryJudgeStage(canaryRun: IExecution): ICanaryJudgeStage {
    return canaryRun.stages.find(s => s.type === CANARY_JUDGE) as ICanaryJudgeStage;
  }
}

export const localCanaryRunService = new LocalCanaryRunService(canaryRuns, metricSetPairLists);
