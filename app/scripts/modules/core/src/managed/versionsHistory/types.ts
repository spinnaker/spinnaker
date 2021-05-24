import { DateTime } from 'luxon';
import { FetchVersionQuery, FetchVersionsHistoryQuery } from '../graphql/graphql-sdk';

export type HistoryEnvironment = NonNullable<FetchVersionsHistoryQuery['application']>['environments'][number];
export type HistoryArtifact = NonNullable<HistoryEnvironment['state']['artifacts']>[number];
export type HistoryArtifactVersion = NonNullable<HistoryArtifact['versions']>[number];

export type SingleVersionEnvironment = NonNullable<FetchVersionQuery['application']>['environments'][number];
export type SingleVersionArtifact = NonNullable<SingleVersionEnvironment['state']['artifacts']>[number];
export type SingleVersionArtifactVersion = NonNullable<SingleVersionArtifact['versions']>[number];

export interface VersionInEnvironment extends HistoryArtifactVersion {
  reference: string;
  type: string;
}

export interface VersionData {
  type: 'BUILD_NUMBER' | 'SHA';
  buildNumbers: Set<string>;
  versions: Set<string>;
  createdAt?: DateTime;
  isBaking?: boolean;
  environments: { [env: string]: { versions: VersionInEnvironment[]; isPinned?: boolean } };
  gitMetadata?: HistoryArtifactVersion['gitMetadata'];
  key: string;
}

export interface PinnedVersions {
  [env: string]: { [artifact: string]: HistoryArtifact['pinnedVersion'] };
}
