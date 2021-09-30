import * as React from 'react';
import type { IScalingPolicyAlarmView } from '../../../../domain';

export interface IAlarmSummaryProps {
  alarm: IScalingPolicyAlarmView;
}

export const comparatorMap = {
  GreaterThanOrEqualToThreshold: '>=',
  GreaterThanThreshold: '>',
  LessThanOrEqualToThreshold: '<=',
  LessThanThreshold: '<',
};

export const AlarmSummary = ({ alarm }: IAlarmSummaryProps) => (
  <div>
    <div>
      <b>Whenever</b>
      {` ${alarm.statistic} of ${alarm.metricName}`}
    </div>
    <div>
      <b>for at least</b>
      {` ${alarm.evaluationPeriods} consecutive periods of ${alarm.period} seconds`}
    </div>
  </div>
);
