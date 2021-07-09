import { IArtifact, IPipeline, IServerGroupCommand, IStage } from '@spinnaker/core';
import { ICloudFoundryEnvVar } from '../../domain';

export interface ICloudFoundryCreateServerGroupCommand extends IServerGroupCommand {
  // clone server group model
  account?: string;
  delayBeforeScaleDownSec?: number;
  rollback?: boolean;
  source?: ICloudFoundrySource;
  target?: string;
  targetCluster?: string;
  targetPercentages?: number[];

  // deploy server group model
  applicationArtifact?: ICloudFoundryArtifact;
  delayBeforeDisableSec?: number;
  manifest?: ICloudFoundryManifest;
  maxRemainingAsgs?: number;
  startApplication: boolean;
}

export interface IViewState {
  mode?: string;
  pipeline?: IPipeline;
  requiresTemplateSelection?: boolean;
  stage?: IStage;
  submitButtonLabel?: string;
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
  clusterName?: string;
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
  applicationArtifact: ICloudFoundryArtifact;
  delayBeforeDisableSec?: number;
  delayBeforeScaleDownSec?: number;
  freeFormDetails?: string;
  manifest: ICloudFoundryManifest;
  maxRemainingAsgs?: number;
  region: string;
  rollback?: boolean;
  stack?: string;
  startApplication: boolean;
  strategy?: string;
  targetPercentages?: number[];
}
