import { IArtifact, IStage } from '../../../../index';

export interface IGoogleCloudBuildStage extends IStage {
  account?: string;
  application?: string;
  buildDefinition?: string;
  buildDefinitionArtifact?: {
    artifact?: IArtifact;
    artifactAccount?: string;
    artifactId?: string;
  };
  buildDefinitionSource?: BuildDefinitionSource;
  repoSource?: {
    [t in TriggerType]?: string;
  };
  triggerId?: string;
  triggerType?: TriggerType;
}

export enum BuildDefinitionSource {
  ARTIFACT = 'artifact',
  TEXT = 'text',
  TRIGGER = 'trigger',
}

export enum TriggerType {
  BRANCH = 'branchName',
  TAG = 'tagName',
  COMMIT = 'commitSha',
}
