import * as React from 'react';
import { Subject } from 'rxjs';

import {
  IAmazonServerGroup,
  IScalingPolicyAlarm,
  ITargetTrackingConfiguration,
  MetricAlarmChart,
} from '@spinnaker/amazon';
import { ICloudMetricStatistics } from '@spinnaker/core';

export interface ITitusTargetTrackingChartProps {
  alarmUpdated?: Subject<void>;
  config: ITargetTrackingConfiguration;
  serverGroup: IAmazonServerGroup;
  unit?: string;
  updateUnit?: (unit: string) => void;
}

export const TitusTargetTrackingChart = ({
  alarmUpdated,
  config,
  serverGroup,
  unit,
  updateUnit,
}: ITitusTargetTrackingChartProps) => {
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
    alarmUpdated?.next();
  };

  return (
    <MetricAlarmChart
      alarm={alarm}
      alarmUpdated={alarmUpdated}
      onChartLoaded={onChartLoaded}
      serverGroup={serverGroup}
    />
  );
};
