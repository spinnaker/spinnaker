import { module } from 'angular';

import { REST } from '@spinnaker/core';
import { IMetricAlarmDescriptor } from './MetricAlarm';

export class MetricAlarmReader {
  public listMetricAlarms(): PromiseLike<IMetricAlarmDescriptor[]> {
    return REST('/ecs/cloudMetrics/alarms').get();
  }
}

export const METRIC_ALARM_READ_SERVICE = 'spinnaker.ecs.metricAlarm.read.service';

module(METRIC_ALARM_READ_SERVICE, []).service('metricAlarmReader', MetricAlarmReader);
