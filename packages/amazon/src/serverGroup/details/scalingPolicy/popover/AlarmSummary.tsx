import * as React from 'react';
import { IScalingPolicyAlarmView } from '../../../../domain';

export interface IAlarmSummaryProps {
  alarm: IScalingPolicyAlarmView;
}

export const AlarmSummary = ({ alarm }: IAlarmSummaryProps) => (
  <div>
    <b>Whenever</b>
    {` ${alarm.statistic} of `}
    <span className="alarm-name">{alarm.metricName}</span>
    {` is ${alarm.comparator} ${alarm.threshold} `}
    <b>for at least</b>
    {` ${alarm.evaluationPeriods} consecutive periods of ${alarm.period} seconds`}
  </div>
);
