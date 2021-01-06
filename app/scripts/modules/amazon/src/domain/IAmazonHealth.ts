import { IHealth, ILoadBalancerHealth } from '@spinnaker/core';

export interface IAmazonHealth extends IHealth {
  targetGroups: IAmazonTargetGroupHealth[];
}

export interface IAmazonTargetGroupHealth extends ILoadBalancerHealth {
  // targetGroups[] have a 'targetGroupName' but not a 'name' field
  targetGroupName: string;
  name: never;
  // Augmented to backend data in applyHealthCheckInfoToTargetGroups()
  healthCheckProtocol?: string;
  healthCheckPath?: string;
}
