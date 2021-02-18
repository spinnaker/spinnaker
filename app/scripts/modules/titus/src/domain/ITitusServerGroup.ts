import { IScalingPolicyView } from '@spinnaker/amazon';
import { IAccountDetails, IServerGroup } from '@spinnaker/core';

import { IJobDisruptionBudget } from './IJobDisruptionBudget';
import { ITitusPolicy } from './ITitusScalingPolicy';
import { ITitusServiceJobProcesses } from './ITitusServiceJobProcesses';

export interface ITitusResources {
  cpu: number;
  disk: number;
  gpu: number;
  memory: number;
  networkMbps: number;
}

export interface ITitusServerGroup extends IServerGroup {
  capacityGroup?: string;
  disruptionBudget?: IJobDisruptionBudget;
  entryPoint: string;
  iamProfile: string;
  id?: string;
  image?: ITitusImage;
  migrationPolicy?: { type: string };
  resources: ITitusResources;
  scalingPolicies?: ITitusPolicy[];
  serviceJobProcesses?: ITitusServiceJobProcesses;
  targetGroups?: string[];
}

export interface ITitusImage {
  dockerImageDigest: string;
  dockerImageName: string;
  dockerImageVersion: string;
}

export interface ITitusServerGroupView extends ITitusServerGroup {
  accountDetails?: IAccountDetails;
  scalingPolicies: IScalingPolicyView[];
}
