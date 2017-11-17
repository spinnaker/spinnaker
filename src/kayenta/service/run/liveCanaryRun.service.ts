import { ReactInjector } from '@spinnaker/core';

import { CanarySettings } from 'kayenta/canary.settings';
import { ICanaryRunService } from './canaryRun.service';
import { IMetricSetPair, ICanaryExecutionStatusResult } from 'kayenta/domain/index';

export class LiveCanaryRunService implements ICanaryRunService {

  public getCanaryRun(configId: string, canaryExecutionId: string): Promise<ICanaryExecutionStatusResult> {
    return ReactInjector.API
      .one('v2/canaries/canary')
      .one(configId)
      .one(canaryExecutionId)
      .withParams({storageAccountName: CanarySettings.storageAccountName})
      .get()
      .then((run: ICanaryExecutionStatusResult) => {
        run.result.config.id = configId;
        run.id = canaryExecutionId;
        return run;
      });
  }

  public getMetricSetPair(metricSetPairListId: string,
                          metricSetPairId: string): Promise<IMetricSetPair> {
    return ReactInjector.API
      .one('v2/canaries/metricSetPairList')
      .one(metricSetPairListId)
      .withParams({storageAccountName: CanarySettings.storageAccountName})
      .get()
      .then(
        (list: IMetricSetPair[]) =>
          list.find(pair => pair.id === metricSetPairId)
      );
  }
}

export const liveCanaryRunService = new LiveCanaryRunService();
