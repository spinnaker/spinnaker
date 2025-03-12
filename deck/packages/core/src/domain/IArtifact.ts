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

export const ARTIFACT_TYPE_EMBEDDED = 'embedded/base64';
export const ARTIFACT_TYPE_REMOTE = 'remote/base64';
