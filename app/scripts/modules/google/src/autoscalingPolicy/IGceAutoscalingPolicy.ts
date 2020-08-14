export interface IGceAutoscalingPolicy {
  cpuUtilization: IGceAutoscalingCpuUtilization;
  scaleInControl: IGceScaleInControl;
}

export interface IGceScaleInControl {
  maxScaledInReplicas?: {
    fixed?: number;
    percent?: number;
  };
  timeWindowSec?: number;
}

export interface IGceAutoscalingCpuUtilization {
  predictiveMethod?: GcePredictiveMethod;
  utilizationTarget?: number;
}

export enum GcePredictiveMethod {
  NONE = 'NONE',
  STANDARD = 'STANDARD',
}
