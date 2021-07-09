import { IAccountDetails, IAsg, IServerGroup } from '@spinnaker/core';

import { IAmazonLaunchTemplate } from './IAmazonLaunchTemplate';
import { IScalingPolicyView } from './IAmazonScalingPolicy';
import { IScalingPolicy } from './IScalingPolicy';
import { ISuspendedProcess } from './IScalingProcess';

export interface IAmazonAsg extends IAsg {
  availabilityZones: string[];
  defaultCooldown: number;
  healthCheckType: string;
  healthCheckGracePeriod: number;
  terminationPolicies: string[];
  enabledMetrics: Array<{ metric: string }>;
  vpczoneIdentifier?: string;
  suspendedProcesses?: ISuspendedProcess[];
  capacityRebalance?: boolean;
}

export interface IAmazonServerGroup extends IServerGroup {
  image?: any;
  scalingPolicies?: IScalingPolicy[];
  targetGroups?: string[];
  asg: IAmazonAsg;
  launchTemplate?: IAmazonLaunchTemplate;
  mixedInstancesPolicy?: IAmazonMixedInstancesPolicy;
}

export interface IScheduledAction {
  recurrence: number;
  minSize: number;
  maxSize: number;
  desiredCapacity: number;
}

export interface IAmazonMixedInstancesPolicy {
  allowedInstanceTypes: string[];
  instancesDiversification: IAmazonInstancesDiversification;
  launchTemplates: IAmazonLaunchTemplate[];
  launchTemplateOverridesForInstanceType: IAmazonLaunchTemplateOverrides[];
}

export interface IAmazonInstancesDiversification {
  onDemandAllocationStrategy: string;
  onDemandBaseCapacity: number;
  onDemandPercentageAboveBaseCapacity: number;
  spotAllocationStrategy: string;
  spotInstancePools?: number;
  spotMaxPrice: string;
}

export interface IAmazonLaunchTemplateOverrides {
  instanceType: string;
  weightedCapacity: string;
}

export interface IAmazonServerGroupView extends IAmazonServerGroup {
  accountDetails?: IAccountDetails;
  scalingPolicies: IScalingPolicyView[];
  scheduledActions?: IScheduledAction[];
}
