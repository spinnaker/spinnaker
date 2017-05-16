import { IServerGroup } from 'core/domain';

export interface IAppengineServerGroup extends IServerGroup {
  disabled: boolean;
  env: 'FLEXIBLE' | 'STANDARD';
  scalingPolicy: IAppengineScalingPolicy;
  servingStatus: 'SERVING' | 'STOPPED';
  allowsGradualTrafficMigration: boolean;
}

export interface IAppengineScalingPolicy {
  type: 'AUTOMATIC' | 'MANUAL' | 'BASIC';
}
