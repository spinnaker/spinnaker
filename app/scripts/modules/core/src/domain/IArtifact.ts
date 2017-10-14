export interface IArtifact {
  type: string;
  name: string;
  version: string;
  location: string;
  reference: string;
  metadata: any;
  artifactAccount: string;
  provenance: string;
  uuid: string;
}
