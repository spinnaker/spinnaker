import { IArtifact } from '../domain';

export interface IArtifactAccountPair {
  id: string;
  account: string;
  artifact?: IArtifact;
}
