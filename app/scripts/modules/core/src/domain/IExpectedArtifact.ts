export interface IExpectedArtifact {
  fields: IArtifactField[];
}

export interface IArtifactField {
  fieldName: string;
  fieldType: FieldType;
  value?: string;
  missingPolicy?: MissingArtifactPolicy;
  expression?: string;
}

export enum FieldType {
  MustMatch = 'MUST_MATCH',
  FindIfMissing = 'FIND_IF_MISSING'
}

export enum MissingArtifactPolicy {
  FailPipeline = 'FAIL_PIPELINE',
  Ignore = 'IGNORE',
  PriorPipeline = 'PRIOR_PIPELINE'
}
