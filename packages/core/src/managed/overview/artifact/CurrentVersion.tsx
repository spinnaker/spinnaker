import React from 'react';

import type { ITaskArtifactVersionProps } from './ArtifactVersionTasks';
import { ArtifactVersionTasks } from './ArtifactVersionTasks';
import { Constraints } from './Constraints';
import { VersionTitle } from './VersionTitle';
import { ArtifactActions } from '../../artifactActions/ArtifactActions';
import type { QueryArtifact, QueryArtifactVersion } from '../types';
import { useCreateVersionRollbackActions } from './useCreateRollbackActions.hook';
import { extractVersionRollbackDetails, isVersionVetoed } from './utils';
import { toPinnedMetadata } from '../../versionMetadata/MetadataComponents';
import { getBaseMetadata, getVersionCompareLinks, VersionMetadata } from '../../versionMetadata/VersionMetadata';

interface ICurrentVersionProps<T = QueryArtifactVersion> {
  data: T;
  environment: string;
  reference: string;
  numNewerVersions?: number;
  pinnedVersion: QueryArtifact['pinnedVersion'];
  isPreview?: boolean;
}

export const CurrentVersionInternal = ({
  data,
  environment,
  reference,
  numNewerVersions,
  pinnedVersion,
  isPreview,
}: ICurrentVersionProps) => {
  const { gitMetadata, constraints, verifications, postDeploy, isCurrent } = data;
  const pinnedMetadata = pinnedVersion?.version === data.version ? toPinnedMetadata(pinnedVersion) : undefined;
  const actions = useCreateVersionRollbackActions({
    environment,
    reference,
    version: data.version,
    isVetoed: isVersionVetoed(data),
    isPinned: Boolean(pinnedVersion),
    isCurrent,
    selectedVersion: extractVersionRollbackDetails(data),
  });

  const versionProps: ITaskArtifactVersionProps = {
    environment,
    reference,
    version: data.version,
    isCurrent,
  };

  return (
    <div className="artifact-current-version">
      <VersionTitle gitMetadata={gitMetadata} buildNumber={data?.buildNumber} version={data.version} />
      <VersionMetadata {...getBaseMetadata(data)} buildsBehind={numNewerVersions} pinned={pinnedMetadata} />
      {!isPreview && (
        <ArtifactActions
          buildNumber={data?.buildNumber}
          version={data.version}
          actions={actions}
          compareLinks={getVersionCompareLinks(data)}
          className="sp-margin-s-yaxis"
        />
      )}
      {constraints && (
        <Constraints constraints={constraints} versionProps={{ environment, reference, version: data.version }} />
      )}
      {verifications && <ArtifactVersionTasks type="Verification" artifact={versionProps} tasks={verifications} />}
      {postDeploy && <ArtifactVersionTasks type="Post deploy" artifact={versionProps} tasks={postDeploy} />}
    </div>
  );
};

export const CurrentVersion = ({ data, ...otherProps }: ICurrentVersionProps<QueryArtifactVersion | undefined>) => {
  return data ? <CurrentVersionInternal data={data} {...otherProps} /> : <div>No version is deployed</div>;
};
