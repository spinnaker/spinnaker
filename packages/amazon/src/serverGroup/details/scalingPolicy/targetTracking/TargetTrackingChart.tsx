import type { Dictionary } from 'lodash';
import * as React from 'react';

import type { ICloudMetricStatistics } from '@spinnaker/core';

import { MetricAlarmChart } from '../chart/MetricAlarmChart';
import type { IAmazonServerGroup, IScalingPolicyAlarm, ITargetTrackingConfiguration } from '../../../../domain';

export interface ITargetTrackingChartProps {
  config: ITargetTrackingConfiguration;
  serverGroup: IAmazonServerGroup;
  unit?: string;
  updateUnit?: (unit: string) => void;
}

const predefinedMetricTypeMapping: Dictionary<string> = {
  ASGAverageCPUUtilization: 'CPUUtilization',
  ASGAverageNetworkIn: 'NetworkIn',
  ASGAverageNetworkOut: 'NetworkOut',
};

export const TargetTrackingChart = ({ config, serverGroup, updateUnit }: ITargetTrackingChartProps) => {
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
      dimensions: customMetric?.dimensions || [{ name: 'AutoScalingGroupName', value: serverGroup.name }],
      metricName:
        customMetric?.metricName ||
        predefinedMetricTypeMapping[config?.predefinedMetricSpecification?.predefinedMetricType],
      namespace: customMetric?.namespace || 'AWS/EC2',
      threshold: config?.targetValue,
    };

    if (customMetric) {
      updatedAlarm.statistic = customMetric?.statistic;
    }

    setAlarm(updatedAlarm);
  };

  React.useEffect(() => {
    synchronizeAlarm();
  }, [config]);

  const onChartLoaded = (stats: ICloudMetricStatistics) => {
    if (updateUnit) {
      updateUnit(stats.unit);
    }
  };

  return <MetricAlarmChart alarm={alarm} onChartLoaded={onChartLoaded} serverGroup={serverGroup} />;
};
