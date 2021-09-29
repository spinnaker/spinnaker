import type { IServerGroup } from '@spinnaker/core';

import type { IGceAutoHealingPolicy } from './autoHealingPolicy';

// TODO(dpeach): fill in the remaining GCE specific properties.
export interface IGceServerGroup extends IServerGroup {
  autoHealingPolicy?: IGceAutoHealingPolicy;
}
