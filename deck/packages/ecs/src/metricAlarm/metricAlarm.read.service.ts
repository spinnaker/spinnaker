import { REST } from '@spinnaker/core';
import type { IMetricAlarmDescriptor } from './MetricAlarm';

export class MetricAlarmReader {
  public listMetricAlarms(): PromiseLike<IMetricAlarmDescriptor[]> {
    return REST('/ecs/cloudMetrics/alarms').get();
  }
}
