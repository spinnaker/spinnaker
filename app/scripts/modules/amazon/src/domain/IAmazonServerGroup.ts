import { IServerGroup, IAsg } from '@spinnaker/core';

import { IScalingPolicy } from './IScalingPolicy';
import { IScalingPolicyView } from 'amazon/domain';

export interface IAmazonAsg extends IAsg {
  defaultCooldown: number;
  healthCheckType: string;
  healthCheckGracePeriod: number;
  terminationPolicies: string[];
  enabledMetrics: { metric: string }[];
}

export interface IAmazonServerGroup extends IServerGroup {
  scalingPolicies?: IScalingPolicy[];
  targetGroups?: string[];
  asg: IAmazonAsg;
}

export interface IAmazonServerGroupView extends IAmazonServerGroup {
  scalingPolicies: IScalingPolicyView[];
}
