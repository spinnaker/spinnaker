import type { IServerGroup } from '@spinnaker/core';

import type { IGceAutoHealingPolicy } from './autoHealingPolicy';
import type { IGceAutoscalingPolicy } from '../autoscalingPolicy';

// TODO(dpeach): fill in the remaining GCE specific properties.
export interface IGceServerGroup extends IServerGroup {
  autoHealingPolicy?: IGceAutoHealingPolicy;
  autoscalingMessages?: string[];
  autoscalingPolicy?: IGceAutoscalingPolicy;
}
