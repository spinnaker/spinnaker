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

export interface ITitusBuildInfo {
  docker: {
    digest: string;
    image: string;
    tag: string;
  };
  images: string[];
  jenkins: {
    commitId?: string;
    host?: string;
    name?: string;
    number?: string;
    version?: string;
  };
}

export interface ITitusServerGroup extends IServerGroup {
  buildInfo: ITitusBuildInfo;
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
