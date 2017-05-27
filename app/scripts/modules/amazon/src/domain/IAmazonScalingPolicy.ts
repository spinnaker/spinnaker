export interface IAmazonScalingAdjustment {
  scalingAdjustment: number;
  operator?: string;
  absAdjustment?: number;
}

export interface IAmazonScalingPolicyAlarm {
  name: string;
  description: string;
  statistic: string;
  metricName: string;
  threshold: number;
  evaluationPeriods: number;
  period: number;
  comparisonOperator: 'LessThanThreshold' | 'GreaterThanThreshold' | 'LessThanOrEqualToThreshold' | 'GreaterThanOrEqualToThreshold';
  comparator?: '&lt;' | '&gt;' | '&le;' | '&ge;';
}

export interface IAmazonScalingPolicy extends IAmazonScalingAdjustment {
  alarms: IAmazonScalingPolicyAlarm[];
  stepAdjustments: IAmazonStepAdjustment[];
}

export interface IAmazonStepAdjustment extends IAmazonScalingAdjustment {
  id: string;
  metricIntervalLowerBound: number;
  metricIntervalUpperBound: number;
}
