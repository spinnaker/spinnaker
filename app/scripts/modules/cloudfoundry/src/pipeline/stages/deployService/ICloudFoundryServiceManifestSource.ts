import { IArtifact } from 'core/domain';

export interface ICloudfoundryServiceManifestDirectSource {
  parameters?: string;
  service: string;
  serviceInstanceName: string;
  servicePlan: string;
  tags?: string[];
}

export interface ICloudFoundryServiceUserProvidedSource {
  credentials?: string;
  routeServiceUrl: string;
  serviceInstanceName: string;
  syslogDrainUrl?: string;
  tags?: string[];
}

export interface ICloudFoundryServiceManifestSource {
  artifact?: IArtifact;
  artifactId?: string;
  direct?: ICloudfoundryServiceManifestDirectSource | ICloudFoundryServiceUserProvidedSource;
}
