export interface IArtifact {
  id: string;
  type?: string;
  name?: string;
  version?: string;
  location?: string;
  reference?: string;
  metadata?: any;
  artifactAccount?: string;
  provenance?: string;
  // Legacy artifacts properties
  kind?: string; // TODO delete
  customKind?: boolean; // TODO delete
}
