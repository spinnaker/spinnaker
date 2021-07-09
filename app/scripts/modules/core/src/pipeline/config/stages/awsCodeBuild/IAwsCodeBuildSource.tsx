import { ArtifactTypePatterns, excludeAllTypesExcept, IArtifact } from '../../../../index';

export interface IAwsCodeBuildSource {
  type?: string;
  sourceArtifact: IAwsCodeBuildSourceArtifact;
  sourceVersion?: string;
}

export interface IAwsCodeBuildSecondarySourcesVersion {
  sourceIdentifier: string;
  sourceVersion: string;
}

interface IAwsCodeBuildSourceArtifact {
  artifactId?: string;
  artifactDisplayName?: string;
  artifactType?: string;
  artifact?: IArtifact;
}

export const SOURCE_TYPES: string[] = ['BITBUCKET', 'CODECOMMIT', 'GITHUB', 'GITHUB_ENTERPRISE', 'S3'];

export const EXCLUDED_ARTIFACT_TYPES: RegExp[] = excludeAllTypesExcept(
  ArtifactTypePatterns.S3_OBJECT,
  ArtifactTypePatterns.GIT_REPO,
);
