import * as React from 'react';

import type { ICloudMetricStatistics } from '@spinnaker/core';
import { CloudMetricsReader, Spinner, useData } from '@spinnaker/core';

import type { IDateLine } from './DateLineChart';
import { DateLineChart } from './DateLineChart';
import type { IAmazonServerGroup, IScalingPolicyAlarm } from '../../../../domain';

interface IMetricAlarmChartProps {
  serverGroup: IAmazonServerGroup;
  alarm: IScalingPolicyAlarm;
  onChartLoaded?: (stats: ICloudMetricStatistics) => void;
}

export function MetricAlarmChart(props: IMetricAlarmChartProps) {
  return props.serverGroup && props.alarm ? <MetricAlarmChartImpl {...props} /> : null;
}

export function MetricAlarmChartImpl(props: IMetricAlarmChartProps) {
  const alarm = props.alarm ?? ({} as IScalingPolicyAlarm);
  const serverGroup = props.serverGroup ?? ({} as IAmazonServerGroup);
  const { account, awsAccount, region, type } = serverGroup;
  const { metricName, namespace, statistic, period } = alarm;

  const { status, result } = useData<ICloudMetricStatistics>(
    async () => {
      const parameters: Record<string, string | number> = { namespace, statistics: statistic, period };
      alarm.dimensions.forEach((dimension) => (parameters[dimension.name] = dimension.value));

      const metricAccount = type === 'aws' ? account : awsAccount;
      const result = await CloudMetricsReader.getMetricStatistics('aws', metricAccount, region, metricName, parameters);
      result.datapoints = result.datapoints || [];
      props.onChartLoaded?.(result);

      return result;
    },
    { datapoints: [], unit: '' },
    [namespace, statistic, period, type, account, region, metricName],
  );

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
