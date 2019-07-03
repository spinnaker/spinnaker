import { IAccountDetails, IServerGroup } from '@spinnaker/core';
import { IScalingPolicyView } from '@spinnaker/amazon';
import { IJobDisruptionBudget } from './IJobDisruptionBudget';
import { ITitusPolicy } from './ITitusScalingPolicy';

export interface ITitusServerGroup extends IServerGroup {
  id?: string;
  disruptionBudget?: IJobDisruptionBudget;
  migrationPolicy?: { type: string };
  image?: ITitusImage;
  scalingPolicies?: ITitusPolicy[];
  targetGroups?: string[];
  capacityGroup?: string;
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
