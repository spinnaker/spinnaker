import { IArtifact } from 'core/domain';

export interface IArtifactAccountPair {
  id: string;
  account: string;
  artifact?: IArtifact;
}
