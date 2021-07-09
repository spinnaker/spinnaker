import { IScalingPolicyAlarmView } from './ITencentcloudScalingPolicy';

export interface IScalingPolicyAlarm {
  dimensions?: any[];
  namespace?: string;
  alarmName?: string;
  alarmArn?: string;
  metricName: string;
  statistic: AlarmStatisticType;
  period: number;
  threshold: number;
  comparisonOperator: AlarmComparisonOperator;
  okactions?: string[];
  insufficientDataActions?: string[];
  alarmActions?: string[];
  continuousTime?: number;
  alarmDescription?: string;
  unit?: StandardUnitType;
}

export type ScalingPolicyAdjustmentType = 'CHANGE_IN_CAPACITY' | 'EXACT_CAPACITY' | 'PERCENT_CHANGE_IN_CAPACITY';

export type MetricAggregationType = 'MINIMUM' | 'MAXIMUM' | 'AVERAGE';

export type AlarmComparisonOperator =
  | 'GREATER_THAN_OR_EQUAL_TO'
  | 'GREATER_THAN'
  | 'LESS_THAN_OR_EQUAL_TO'
  | 'LESS_THAN';

export type AlarmStatisticType = 'SampleCount' | 'AVERAGE' | 'Sum' | 'MINIMUM' | 'MAXIMUM';

export type StandardUnitType =
  | 'Seconds'
  | 'Microseconds'
  | 'Milliseconds'
  | 'Bytes'
  | 'Kilobytes'
  | 'Megabytes'
  | 'Gigabytes'
  | 'Terabytes'
  | 'Bits'
  | 'Kilobits'
  | 'Megabits'
  | 'Gigabits'
  | 'Terabits'
  | 'Percent'
  | 'Count'
  | 'BytesSecond'
  | 'KilobytesSecond'
  | 'MegabytesSecond'
  | 'GigabytesSecond'
  | 'TerabytesSecond'
  | 'BitsSecond'
  | 'KilobitsSecond'
  | 'MegabitsSecond'
  | 'GigabitsSecond'
  | 'TerabitsSecond'
  | 'CountSecond'
  | 'None';

export interface IStepAdjustment {
  metricIntervalLowerBound?: number;
  metricIntervalUpperBound?: number;
  adjustmentValue?: number;
}

export interface IScalingPolicy {
  alarms?: IScalingPolicyAlarmView[];
  policyARN?: string;
  policyName?: string;
  policyType?: string;
  adjustmentType?: ScalingPolicyAdjustmentType;
  metricAlarm: IScalingPolicyAlarm;
  autoScalingPolicyId?: string;
  stepAdjustments?: IStepAdjustment[]; // step
  metricAggregationType?: MetricAggregationType; // step
  estimatedInstanceWarmup?: number; // step

  minAdjustmentStep?: number; // simple
  cooldown?: number; // simple
  minAdjustmentMagnitude?: number; // simple
  adjustmentValue?: number; // simple
}
