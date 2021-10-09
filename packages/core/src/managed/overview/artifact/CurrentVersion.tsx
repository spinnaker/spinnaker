import React from 'react';

import type { ITaskArtifactVersionProps } from './ArtifactVersionTasks';
import { ArtifactVersionTasks } from './ArtifactVersionTasks';
import { Constraints } from './Constraints';
import { VersionTitle } from './VersionTitle';
import { ArtifactActions } from '../../artifactActions/ArtifactActions';
import type { QueryArtifactVersion } from '../types';
import { useCreateVersionRollbackActions } from './useCreateRollbackActions.hook';
import { extractVersionRollbackDetails, isVersionVetoed } from './utils';
import type { VersionMessageData } from '../../versionMetadata/MetadataComponents';
import { getBaseMetadata, getVersionCompareLinks, VersionMetadata } from '../../versionMetadata/VersionMetadata';

interface ICurrentVersionProps {
  data: QueryArtifactVersion;
  environment: string;
  reference: string;
  numNewerVersions?: number;
  pinned?: VersionMessageData;
  isPreview?: boolean;
}

export const CurrentVersion = ({
  data,
  environment,
  reference,
  numNewerVersions,
  pinned,
  isPreview,
}: ICurrentVersionProps) => {
  const { gitMetadata, constraints, verifications, postDeploy, isCurrent } = data;
  const actions = useCreateVersionRollbackActions({
    environment,
    reference,
    version: data.version,
    isVetoed: isVersionVetoed(data),
    isPinned: Boolean(pinned),
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
      <VersionMetadata {...getBaseMetadata(data)} buildsBehind={numNewerVersions} pinned={pinned} />
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
