import type { IServerGroup } from '@spinnaker/core';

import type { IGceAutoHealingPolicy } from './autoHealingPolicy';
import type { IGceAutoscalingPolicy } from '../autoscalingPolicy';

export interface IGceInstanceSelection {
  rank?: number;
  machineTypes: string[];
}

/**
 * Named map of flexibility selections. Keys are selection names; values carry optional
 * preference rank and one or more machine types. Rank may be omitted for equal preference.
 */
export interface IGceInstanceFlexibilityPolicy {
  instanceSelections: { [selectionName: string]: IGceInstanceSelection };
}

export interface IGceDistributionPolicy {
  zones?: string[];
  targetShape?: string;
}

// TODO(dpeach): fill in the remaining GCE specific properties.
export interface IGceServerGroup extends IServerGroup {
  autoHealingPolicy?: IGceAutoHealingPolicy;
  autoscalingMessages?: string[];
  autoscalingPolicy?: IGceAutoscalingPolicy;
  distributionPolicy?: IGceDistributionPolicy;
  instanceFlexibilityPolicy?: IGceInstanceFlexibilityPolicy;
  selectZones?: boolean;
  regional?: boolean;
}
