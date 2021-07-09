import { FetchApplicationQuery } from '../graphql/graphql-sdk';

export type QueryEnvironment = NonNullable<FetchApplicationQuery['application']>['environments'][number];

export type QueryArtifact = NonNullable<QueryEnvironment['state']['artifacts']>[number];
export type QueryResource = NonNullable<QueryEnvironment['state']['resources']>[number];

export type QueryArtifactVersion = NonNullable<QueryArtifact['versions']>[number];

export type QueryLifecycleStep = NonNullable<QueryArtifactVersion['lifecycleSteps']>[number];

export type QueryGitMetadata = QueryArtifactVersion['gitMetadata'];
export type QueryConstraint = NonNullable<QueryArtifactVersion['constraints']>[number];
export type QueryArtifactVersionTask =
  | NonNullable<QueryArtifactVersion['verifications']>[number]
  | NonNullable<QueryArtifactVersion['postDeploy']>[number];
export type QueryArtifactVersionTaskStatus = QueryArtifactVersionTask['status'];

export interface ArtifactVersionProps {
  environment: string;
  reference: string;
  version: string;
}
