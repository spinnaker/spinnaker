import { IScalingPolicy, IScalingPolicyAlarm, IStepAdjustment } from 'amazon/domain';

export interface IScalingAdjustmentView extends IStepAdjustment {
  operator: string;
  absAdjustment: number;
}

export interface IScalingPolicyAlarmView extends IScalingPolicyAlarm {
  name?: string;
  description?: string;
  comparator?: '&lt;' | '&gt;' | '&le;' | '&ge;';
}

export interface IScalingPolicyView extends IScalingPolicy, IScalingAdjustmentView {
  alarms: IScalingPolicyAlarmView[];
  stepAdjustments: IStepAdjustmentView[];
  operator: string;
}

export interface IStepAdjustmentView extends IScalingAdjustmentView, IStepAdjustment {
  metricIntervalLowerBound: number;
  metricIntervalUpperBound: number;
}
