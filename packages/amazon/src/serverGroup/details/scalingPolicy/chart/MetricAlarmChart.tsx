import { module } from 'angular';
import * as React from 'react';
import { react2angular } from 'react2angular';
import { Observable } from 'rxjs';

import {
  CloudMetricsReader,
  ICloudMetricStatistics,
  Spinner,
  useData,
  useForceUpdate,
  useObservable,
} from '@spinnaker/core';
import { withErrorBoundary } from '@spinnaker/core';

import { DateLineChart, IDateLine } from './DateLineChart';
import { IAmazonServerGroup, IScalingPolicyAlarm } from '../../../../domain';

interface IMetricAlarmChartProps {
  serverGroup: IAmazonServerGroup;
  alarm: IScalingPolicyAlarm;
  // Allows AngularJS to tell the chart to update
  alarmUpdated?: Observable<any>;
  // Allows the chart data to inform the parent component of the fetched metric units
  onChartLoaded?: (stats: ICloudMetricStatistics) => void;
}

export function MetricAlarmChart(props: IMetricAlarmChartProps) {
  return props.serverGroup && props.alarm ? <MetricAlarmChartImpl {...props} /> : null;
}

export function MetricAlarmChartImpl(props: IMetricAlarmChartProps) {
  const alarm = props.alarm ?? ({} as IScalingPolicyAlarm);
  const serverGroup = props.serverGroup ?? ({} as IAmazonServerGroup);
  const { type, account, region } = serverGroup;
  const { metricName, namespace, statistic, period } = alarm;

  const { status, result } = useData<ICloudMetricStatistics>(
    async () => {
      const parameters: Record<string, string | number> = { namespace, statistics: statistic, period };
      alarm.dimensions.forEach((dimension) => (parameters[dimension.name] = dimension.value));

      const result = await CloudMetricsReader.getMetricStatistics(type, account, region, metricName, parameters);
      result.datapoints = result.datapoints || [];
      props.onChartLoaded?.(result);

      return result;
    },
    { datapoints: [], unit: '' },
    [namespace, statistic, period, type, account, region, metricName],
  );

  // Used by AngularJS to tell the chart to refresh, delete when all callers are reactified
  const forceUpdate = useForceUpdate();
  useObservable(props.alarmUpdated, () => forceUpdate());

  if (status === 'PENDING') {
    return (
      <div className="flex-container-v middle center sp-margin-xl">
        <Spinner />
      </div>
    );
  } else if (status === 'REJECTED') {
    // Metrics with a "%" in the name such as "EBSIOBalance%" return 500
    // by spring boot due to a security check.  For now just say "no data" :(
    return (
      <>
        <div>no data</div>
        <div>something went wrong fetching stats</div>
      </>
    );
  } else if (result.datapoints.length === 0) {
    return <>no data</>;
  }

  const now = new Date();
  const oneDayAgo = new Date(Date.now() - 1000 * 60 * 60 * 24);

  const line: IDateLine = {
    label: metricName,
    fill: 'stack',
    borderColor: 'green',
    borderWidth: 2,
    data: result.datapoints.map((dp) => ({ x: new Date(dp.timestamp), y: dp.average })),
  };

  const setline: IDateLine = {
    label: 'threshold',
    borderWidth: 1,
    borderColor: 'red',
    data: [
      { x: oneDayAgo, y: alarm.threshold },
      { x: now, y: alarm.threshold },
    ],
  };
  return <DateLineChart lines={[line, setline]} />;
}

export const AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_CHART_METRICALARMCHART_COMPONENT =
  'spinnaker.amazon.serverGroup.details.scalingPolicy.metricAlarmChart.component';
export const name = AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_CHART_METRICALARMCHART_COMPONENT; // for backwards compatibility
module(AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_CHART_METRICALARMCHART_COMPONENT, []).component(
  'metricAlarmChart',
  react2angular(withErrorBoundary(MetricAlarmChart, 'metricAlarmChart'), [
    'alarm',
    'serverGroup',
    'alarmUpdated',
    'onChartLoaded',
  ]),
);
