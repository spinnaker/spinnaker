import { IArtifact } from '@spinnaker/core';

export interface ICloudfoundryServiceManifestDirectSource {
  parameters?: string;
  service: string;
  serviceInstanceName: string;
  servicePlan: string;
  tags?: string[];
  updatable: boolean;
  versioned: boolean;
}

export interface ICloudFoundryServiceUserProvidedSource {
  credentials?: string;
  routeServiceUrl: string;
  serviceInstanceName: string;
  syslogDrainUrl?: string;
  tags?: string[];
  updatable: boolean;
  versioned: boolean;
}

export interface ICloudFoundryServiceManifestSource {
  artifact?: IArtifact;
  artifactId?: string;
  direct?: ICloudfoundryServiceManifestDirectSource | ICloudFoundryServiceUserProvidedSource;
}
