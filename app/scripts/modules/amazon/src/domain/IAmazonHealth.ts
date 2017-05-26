import { IHealth } from '@spinnaker/core';

import { ITargetGroup } from 'amazon/domain';

export interface IAmazonHealth extends IHealth {
  targetGroups: ITargetGroup[];
}
