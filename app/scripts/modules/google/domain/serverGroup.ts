import { IServerGroup } from '@spinnaker/core';

import { IGceAutoHealingPolicy } from './autoHealingPolicy';

// TODO(dpeach): fill in the remaining GCE specific properties.
export interface IGceServerGroup extends IServerGroup {
  autoHealingPolicy?: IGceAutoHealingPolicy;
}
