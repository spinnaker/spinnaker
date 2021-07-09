import { AlarmStatisticType, IScalingPolicy } from './IScalingPolicy';

export interface ITargetTrackingPolicy extends IScalingPolicy {
  autoScalingGroupName?: string;
  targetTrackingConfiguration: ITargetTrackingConfiguration;
}

export interface ITargetTrackingConfiguration {
  customizedMetricSpecification?: ICustomizedMetricSpecification;
  predefinedMetricSpecification?: IPredefinedMetricSpecification;
  disableScaleIn?: boolean;
  targetValue: number;
}

export interface ICustomizedMetricSpecification {
  metricName: string;
  namespace: string;
  dimensions: any[];
  statistic: AlarmStatisticType;
}

export interface IPredefinedMetricSpecification {
  predefinedMetricType: PredefinedMetricType;
}

export type PredefinedMetricType = 'ASGAverageCPUUtilization' | 'ASGAverageNetworkIn' | 'ASGAverageNetworkOut';
