import { IServerGroupCommand } from '@spinnaker/core';

import { ICloudFoundryCluster, ICloudFoundryEnvVar } from 'cloudfoundry/domain';

export interface ICloudFoundryCreateServerGroupCommand extends IServerGroupCommand {
  artifact: ICloudFoundryBinarySource;
  manifest: ICloudFoundryManifestSource;
  startApplication: boolean;
}

export interface ICloudFoundryArtifactSource {
  reference: string;
  account: string;
}

export interface ICloudFoundryPackageSource {
  cluster: ICloudFoundryCluster;
  serverGroupName: string;
  account: string;
  region: string;
}

export interface ICloudFoundryTriggerSource {
  pattern: string;
  account: string; // optional: used in the event that retrieving an artifact from a trigger source requires auth
}

export type ICloudFoundryBinarySource = { type: string } & (
  | ICloudFoundryArtifactSource
  | ICloudFoundryPackageSource
  | ICloudFoundryTriggerSource);

export interface ICloudFoundryManifestDirectSource {
  memory: string;
  diskQuota: string;
  instances: number;
  buildpack: string;
  routes: string[];
  env: ICloudFoundryEnvVar[];
  services: string[];
}

export interface ICloudFoundryManifestArtifactSource {
  reference: string;
  account: string;
}

export interface ICloudFoundryManifestTriggerSource {
  pattern: string;
  account: string; // optional: used in the event that retrieving a manifest from a trigger source requires auth
}

export type ICloudFoundryManifestSource = { type: string } & (
  | ICloudFoundryManifestDirectSource
  | ICloudFoundryManifestTriggerSource
  | ICloudFoundryManifestArtifactSource);

export interface ICloudFoundryDeployConfiguration {
  account: string;
  application: string;
  artifact: ICloudFoundryBinarySource;
  manifest: ICloudFoundryManifestSource;
  region: string;
  stack?: string;
  freeFormDetails?: string;
  strategy?: string;
  startApplication: boolean;
}
