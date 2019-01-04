export interface ICloudfoundryServiceManifestDirectSource {
  parameters?: string;
  service: string;
  serviceName: string;
  servicePlan: string;
  tags?: string[];
}

export interface ICloudfoundryServiceManifestArtifactSource {
  account: string;
  reference: string;
}

export interface ICloudFoundryServiceUserProvidedSource {
  credentials?: string;
  routeServiceUrl: string;
  serviceName: string;
  syslogDrainUrl?: string;
  tags?: string[];
}

export type ICloudFoundryServiceManifestSource = { type: string } & (
  | ICloudfoundryServiceManifestDirectSource
  | ICloudfoundryServiceManifestArtifactSource
  | ICloudFoundryServiceUserProvidedSource);
