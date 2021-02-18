import { IAccountDetails, IAsg, IServerGroup } from '@spinnaker/core';

import { IScalingPolicyView, ISuspendedProcess } from '.';
import { IScalingPolicy } from './IScalingPolicy';

export interface ITencentcloudAsg extends IAsg {
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

export interface ITencentcloudServerGroup extends IServerGroup {
  [x: string]: any;
  vpcName?: any;
  image?: any;
  scalingPolicies?: IScalingPolicy[];
  targetGroups?: string[];
  asg?: ITencentcloudAsg;
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

export interface ITencentcloudServerGroupView extends ITencentcloudServerGroup {
  accountDetails?: IAccountDetails;
  scalingPolicies: IScalingPolicyView[];
  scheduledActions?: IScheduledAction[];
}
