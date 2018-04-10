import { module } from 'angular';

import { API_SERVICE, Api } from 'core/api/api.service';
import { IMetricAlarmDescriptor } from './MetricAlarm';

export class MetricAlarmReader {
  public constructor(private API: Api) {
    'ngInject';
  }

  public listMetricAlarms(): ng.IPromise<IMetricAlarmDescriptor[]> {
    return this.API.all('ecs')
      .all('cloudMetrics')
      .all('alarms')
      .getList();
  }
}

export const METRIC_ALARM_READ_SERVICE = 'spinnaker.ecs.metricAlarm.read.service';

module(METRIC_ALARM_READ_SERVICE, [API_SERVICE]).service('metricAlarmReader', MetricAlarmReader);
