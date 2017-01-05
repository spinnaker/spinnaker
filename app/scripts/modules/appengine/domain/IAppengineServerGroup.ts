import {ServerGroup} from 'core/domain/index';

export interface IAppengineServerGroup extends ServerGroup {
  disabled: boolean;
  env: 'FLEXIBLE' | 'STANDARD';
  scalingPolicy: IAppengineScalingPolicy;
  servingStatus: 'SERVING' | 'STOPPED';
}

export interface IAppengineScalingPolicy {
  type: 'AUTOMATIC' | 'MANUAL' | 'BASIC';
}
