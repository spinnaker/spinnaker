import { IServerGroup } from '@spinnaker/core';

import { IAmazonScalingPolicy } from './IAmazonScalingPolicy';

export interface IAmazonServerGroup extends IServerGroup {
  scalingPolicies?: IAmazonScalingPolicy[];
  targetGroups?: string[];
}
