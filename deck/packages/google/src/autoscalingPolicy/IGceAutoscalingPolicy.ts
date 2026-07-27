export interface IGceAutoscalingPolicy {
  minNumReplicas?: number;
  maxNumReplicas?: number;
  coolDownPeriodSec?: number;
  mode?: 'ON' | 'OFF' | 'ONLY_SCALE_OUT';
  cpuUtilization?: IGceAutoscalingCpuUtilization;
  loadBalancingUtilization?: IGceAutoscalingUtilization;
  customMetricUtilizations?: IGceAutoscalingCustomMetric[];
  scaleInControl?: IGceScaleInControl;
  scalingSchedules?: IGceScalingSchedule[];
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

export interface IGceAutoscalingUtilization {
  utilizationTarget?: number;
}

export type GceUtilizationTargetType = 'GAUGE' | 'DELTA_PER_SECOND' | 'DELTA_PER_MINUTE';
export type GceMetricExportScope = 'TIME_SERIES_PER_INSTANCE' | 'SINGLE_TIME_SERIES_PER_GROUP';
export type GceScalingPolicyType = 'UTILIZATION_TARGET' | 'SINGLE_INSTANCE_ASSIGNMENT';

export interface IGceAutoscalingCustomMetric {
  metric?: string;
  filter?: string;
  metricExportScope?: GceMetricExportScope;
  scalingpolicy?: GceScalingPolicyType;
  singleInstanceAssignment?: number;
  utilizationTarget?: number;
  utilizationTargetType?: GceUtilizationTargetType;
}

export interface IGceScalingSchedule {
  scheduleName?: string;
  scheduleDescription?: string;
  enabled?: boolean;
  minimumRequiredInstances?: number;
  scheduleCron?: string;
  timezone?: string;
  duration?: number;
}

export enum GcePredictiveMethod {
  NONE = 'NONE',
  STANDARD = 'OPTIMIZE_AVAILABILITY',
}
