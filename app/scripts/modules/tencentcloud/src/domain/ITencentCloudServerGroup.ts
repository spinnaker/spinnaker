import { IAccountDetails, IServerGroup, IAsg } from '@spinnaker/core';

import { ISuspendedProcess, IScalingPolicyView } from '.';

import { IScalingPolicy } from './IScalingPolicy';

export interface ITencentCloudAsg extends IAsg {
  availabilityZones: string[];
  defaultCooldown: number;
  terminationPolicies: string[];
  enabledMetrics: Array<{ metric: string }>;
  vpczoneIdentifier?: string;
  suspendedProcesses?: ISuspendedProcess[];
  zoneSet?: string[];
  terminationPolicySet: string[];
  vpcId: string;
  subnetIdSet: string[];
  instanceCount?: string;
}

export interface ITencentCloudServerGroup extends IServerGroup {
  [x: string]: any;
  vpcName?: any;
  image?: any;
  scalingPolicies?: IScalingPolicy[];
  targetGroups?: string[];
  asg?: ITencentCloudAsg;
  accountName?: string;
  instanceCount?: number;
}

export interface IScheduledAction {
  scheduledActionId?: string;
  scheduledActionName?: string;
  startTime?: string;
  endTime?: string;
  recurrence: number;
  minSize: number;
  maxSize: number;
  desiredCapacity: number;
}

export interface ITencentCloudServerGroupView extends ITencentCloudServerGroup {
  accountDetails?: IAccountDetails;
  scalingPolicies: IScalingPolicyView[];
  scheduledActions?: IScheduledAction[];
}
