import { IServerGroup } from '@spinnaker/core';

import { IScalingPolicy } from './IScalingPolicy';
import { IScalingPolicyView } from 'amazon/domain';

export interface IAmazonServerGroup extends IServerGroup {
  scalingPolicies?: IScalingPolicy[];
  targetGroups?: string[];
}

export interface IAmazonServerGroupView extends IAmazonServerGroup {
  scalingPolicies: IScalingPolicyView[];
}
