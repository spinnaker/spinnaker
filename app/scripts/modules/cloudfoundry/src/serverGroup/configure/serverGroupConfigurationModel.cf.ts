import { IServerGroupCommand } from '@spinnaker/core';

import { ICloudFoundryEnvVar } from 'cloudfoundry/domain';

export interface ICloudFoundryCreateServerGroupCommand extends IServerGroupCommand {
  artifact: ICloudFoundryBinarySource;
  delayBeforeDisableSec?: number;
  manifest: ICloudFoundryManifestSource;
  maxRemainingAsgs?: number;
  rollback?: boolean;
  startApplication: boolean;
}

export interface ICloudFoundryArtifactSource {
  reference: string;
  account: string;
}

export interface ICloudFoundryPackageSource {
  clusterName: string;
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
  healthCheckType: string;
  healthCheckHttpEndpoint: string;
  routes: string[];
  environment: ICloudFoundryEnvVar[];
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
  delayBeforeDisableSec?: number;
  manifest: ICloudFoundryManifestSource;
  maxRemainingAsgs?: number;
  region: string;
  rollback?: boolean;
  stack?: string;
  freeFormDetails?: string;
  strategy?: string;
  startApplication: boolean;
}
