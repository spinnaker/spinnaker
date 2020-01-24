import { IArtifact } from './IArtifact';

export interface IExpectedArtifact {
  matchArtifact: IArtifact;
  usePriorArtifact: boolean;
  useDefaultArtifact: boolean;
  defaultArtifact: IArtifact;
  boundArtifact?: IArtifact;
  displayName: string;
  id: string;
}
