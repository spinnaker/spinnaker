import type { IScalingPolicy, IScalingPolicyAlarm, IStepAdjustment } from './IScalingPolicy';
import type { ITargetTrackingConfiguration } from './ITargetTrackingPolicy';

export interface IScalingAdjustmentView extends IStepAdjustment {
  operator: string;
  absAdjustment: number;
}

export interface IScalingPolicyAlarmView extends IScalingPolicyAlarm {
  name?: string;
  description?: string;
  disableEditingDimensions?: boolean;
  comparator?: '&lt;' | '&gt;' | '&le;' | '&ge;';
}

export interface IScalingPolicyView extends IScalingPolicy, IScalingAdjustmentView {
  alarms: IScalingPolicyAlarmView[];
  stepAdjustments: IStepAdjustmentView[];
  operator: string;
  targetTrackingConfiguration?: ITargetTrackingConfiguration;
}

export interface IStepAdjustmentView extends IScalingAdjustmentView, IStepAdjustment {
  metricIntervalLowerBound: number;
  metricIntervalUpperBound: number;
}
