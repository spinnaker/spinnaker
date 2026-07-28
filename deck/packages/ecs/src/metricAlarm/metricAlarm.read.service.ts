import { REST } from '@spinnaker/core';
import type { IMetricAlarmDescriptor } from './MetricAlarm';

export class MetricAlarmReader {
  public listMetricAlarms(): Promise<IMetricAlarmDescriptor[]> {
    return REST('/ecs/cloudMetrics/alarms').get();
  }
}
