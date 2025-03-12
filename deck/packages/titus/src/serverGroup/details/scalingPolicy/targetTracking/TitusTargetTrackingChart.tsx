import * as React from 'react';

import type { IAmazonServerGroup, IScalingPolicyAlarm, ITargetTrackingConfiguration } from '@spinnaker/amazon';
import { MetricAlarmChart } from '@spinnaker/amazon';
import type { ICloudMetricStatistics } from '@spinnaker/core';

export interface ITitusTargetTrackingChartProps {
  config: ITargetTrackingConfiguration;
  serverGroup: IAmazonServerGroup;
  unit?: string;
  updateUnit?: (unit: string) => void;
}

export const TitusTargetTrackingChart = ({ config, serverGroup, unit, updateUnit }: ITitusTargetTrackingChartProps) => {
  const [alarm, setAlarm] = React.useState<IScalingPolicyAlarm>({
    alarmName: null,
    alarmArn: null,
    metricName: null,
    namespace: null,
    statistic: 'Average',
    dimensions: [],
    period: 60,
    threshold: config.targetValue,
    comparisonOperator: 'GreaterThanThreshold',
    okactions: [],
    insufficientDataActions: [],
    alarmActions: [],
    evaluationPeriods: null,
    alarmDescription: null,
    unit: null,
  });

  const synchronizeAlarm = () => {
    const customMetric = config?.customizedMetricSpecification;
    const updatedAlarm = {
      ...alarm,
      dimensions: customMetric?.dimensions,
      metricName: customMetric?.metricName,
      namespace: customMetric?.namespace,
      statistic: customMetric?.statistic,
      threshold: config?.targetValue,
    };
    setAlarm(updatedAlarm);
  };

  React.useEffect(() => {
    synchronizeAlarm();
  }, [config]);

  const onChartLoaded = (stats: ICloudMetricStatistics) => {
    if (unit) {
      updateUnit(stats.unit);
    }
  };

  return <MetricAlarmChart alarm={alarm} onChartLoaded={onChartLoaded} serverGroup={serverGroup} />;
};
