import { IHealth } from '@spinnaker/core';

import { ITargetGroup } from './IAmazonLoadBalancer';

export interface IAmazonHealth extends IHealth {
  targetGroups: ITargetGroup[];
  type: string;
}
