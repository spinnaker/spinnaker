import type { IAccountDetails, IAsg, IServerGroup } from '@spinnaker/core';

import type { IAmazonLaunchTemplate } from './IAmazonLaunchTemplate';
import type { IScalingPolicyView } from './IAmazonScalingPolicy';
import type { IScalingPolicy } from './IScalingPolicy';
import type { ISuspendedProcess } from './IScalingProcess';

export interface IAmazonAsgTag {
  key: string;
  value: string;
  propagateAtLaunch: boolean;
  resourceId: string;
  resourceType: string;
}

export interface IAmazonAsg extends IAsg {
  availabilityZones: string[];
  autoScalingGroupName: string;
  defaultCooldown: number;
  healthCheckType: string;
  healthCheckGracePeriod: number;
  loadBalancerNames: string[];
  terminationPolicies: string[];
  enabledMetrics: Array<{ metric: string }>;
  vpczoneIdentifier?: string;
  suspendedProcesses?: ISuspendedProcess[];
  capacityRebalance?: boolean;
  tags: IAmazonAsgTag[];
}

export interface IAmazonServerGroup extends IServerGroup {
  image?: any;
  scalingPolicies?: IScalingPolicy[];
  targetGroups?: string[];
  asg: IAmazonAsg;
  awsAccount?: string;
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
  instancesDistribution: IAmazonInstancesDistribution;
  launchTemplates: IAmazonLaunchTemplate[];
  launchTemplateOverridesForInstanceType: IAmazonLaunchTemplateOverrides[];
}

export interface IAmazonInstancesDistribution {
  onDemandAllocationStrategy: string;
  onDemandBaseCapacity: number;
  onDemandPercentageAboveBaseCapacity: number;
  spotAllocationStrategy: string;
  spotInstancePools?: number;
  spotMaxPrice: string;
}

export interface IAmazonLaunchTemplateOverrides {
  instanceType: string;
  weightedCapacity?: string;
  priority?: number;
}

export interface IAmazonServerGroupView extends IAmazonServerGroup {
  accountDetails?: IAccountDetails;
  scalingPolicies: IScalingPolicyView[];
  scheduledActions?: IScheduledAction[];
}
