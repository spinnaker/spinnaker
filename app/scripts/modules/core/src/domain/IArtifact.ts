export interface IArtifact {
  kind?: string; // json model only
  id: string;
  type?: string;
  name?: string;
  version?: string;
  location?: string;
  reference?: string;
  metadata?: any;
  artifactAccount?: string;
  provenance?: string;
}
