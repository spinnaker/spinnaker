import { DateTime } from 'luxon';
import { FetchVersionsHistoryQuery } from '../graphql/graphql-sdk';

export type HistoryEnvironment = NonNullable<FetchVersionsHistoryQuery['application']>['environments'][number];
export type HistoryArtifact = NonNullable<HistoryEnvironment['state']['artifacts']>[number];
export type HistoryArtifactVersion = NonNullable<HistoryArtifact['versions']>[number];

export interface VersionInEnvironment extends HistoryArtifactVersion {
  reference: string;
  type: string;
}

export interface VersionData {
  type: 'BUILD_NUMBER' | 'SHA';
  buildNumbers: Set<string>;
  versions: Set<string>;
  createdAt?: DateTime;
  environments: { [env: string]: VersionInEnvironment[] };
  gitMetadata?: HistoryArtifactVersion['gitMetadata'];
  key: string;
}

export interface PinnedVersions {
  [env: string]: { [artifact: string]: HistoryArtifact['pinnedVersion'] };
}
