import { IAccountDetails, IServerGroup } from '@spinnaker/core';
import { IScalingPolicyView } from '@spinnaker/amazon';
import { ITitusPolicy } from './ITitusScalingPolicy';

export interface ITitusServerGroup extends IServerGroup {
  image?: ITitusImage;
  scalingPolicies?: ITitusPolicy[];
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
