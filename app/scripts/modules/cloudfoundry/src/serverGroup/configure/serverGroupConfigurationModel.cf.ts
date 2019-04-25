import { IServerGroupCommand, IArtifact } from '@spinnaker/core';
import { ICloudFoundryEnvVar } from 'cloudfoundry/domain';

export interface ICloudFoundryCreateServerGroupCommand extends IServerGroupCommand {
  // clone server group model
  account?: string;
  source?: ICloudFoundrySource;
  rollback?: boolean;
  target?: string;
  targetCluster?: string;

  // deploy server group model
  delayBeforeDisableSec?: number;
  applicationArtifact?: ICloudFoundryArtifact;
  manifest?: ICloudFoundryManifest;
  maxRemainingAsgs?: number;
  startApplication: boolean;
}

export interface ICloudFoundryArtifact {
  // one of these two are required
  artifact?: IArtifact;
  artifactId?: string;
}

export interface ICloudFoundryManifest {
  // one of these three are required
  direct: ICloudFoundryManifestDirectSource;
  artifactId?: string;
  artifact?: IArtifact;
}

export interface ICloudFoundrySource {
  asgName: string;
  region: string;
  account: string;
  targetCluster?: string;
}

export interface ICloudFoundryManifestDirectSource {
  memory: string;
  diskQuota: string;
  instances: number;
  buildpacks: string[];
  healthCheckType?: string;
  healthCheckHttpEndpoint?: string;
  routes: string[];
  environment: ICloudFoundryEnvVar[];
  services: string[];
}

export interface ICloudFoundryDeployConfiguration {
  account: string;
  application: string;
  delayBeforeDisableSec?: number;
  applicationArtifact: ICloudFoundryArtifact;
  manifest: ICloudFoundryManifest;
  maxRemainingAsgs?: number;
  region: string;
  rollback?: boolean;
  stack?: string;
  freeFormDetails?: string;
  strategy?: string;
  startApplication: boolean;
}
