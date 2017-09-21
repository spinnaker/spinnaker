export interface IExpectedArtifact {
  name: string;
  type: string;
  missingPolicy: MissingArtifactPolicy;
}

export enum MissingArtifactPolicy {
  FailPipeline = 'FailPipeline',
  Ignore = 'Ignore'
}
