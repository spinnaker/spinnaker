import { IMetricAlarmDimension } from '@spinnaker/core';

export interface IScalingPolicyAlarm {
  alarmName?: string;
  alarmArn?: string;
  metricName: string;
  namespace: string;
  statistic: AlarmStatisticType;
  dimensions: IMetricAlarmDimension[];
  period: number;
  threshold: number;
  comparisonOperator: AlarmComparisonOperator;
  okactions?: string[];
  insufficientDataActions?: string[];
  alarmActions?: string[];
  evaluationPeriods?: number;
  alarmDescription?: string;
  unit?: StandardUnitType;
}

export type ScalingPolicyAdjustmentType = 'ChangeInCapacity' | 'ExactCapacity' | 'PercentChangeInCapacity';

export type MetricAggregationType = 'Minimum' | 'Maximum' | 'Average';

export type AlarmComparisonOperator =
  | 'GreaterThanOrEqualToThreshold'
  | 'GreaterThanThreshold'
  | 'LessThanThreshold'
  | 'LessThanOrEqualToThreshold';

export type AlarmStatisticType = 'SampleCount' | 'Average' | 'Sum' | 'Minimum' | 'Maximum';

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
  scalingAdjustment?: number;
}

export interface IScalingPolicy {
  policyARN?: string;
  policyName?: string;
  policyType?: string;
  adjustmentType?: ScalingPolicyAdjustmentType;
  alarms: IScalingPolicyAlarm[];

  stepAdjustments?: IStepAdjustment[]; // step
  metricAggregationType?: MetricAggregationType; // step
  estimatedInstanceWarmup?: number; // step

  minAdjustmentStep?: number; // simple
  cooldown?: number; // simple
  minAdjustmentMagnitude?: number; // simple
  scalingAdjustment?: number; // simple
}
