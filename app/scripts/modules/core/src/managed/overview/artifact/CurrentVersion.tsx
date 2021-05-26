import React from 'react';

import { ArtifactVersionTasks } from './ArtifactVersionTasks';
import { Constraints } from './Constraints';
import { GitLink } from './GitLink';
import { QueryArtifactVersion } from '../types';
import { useCreateVersionActions } from './utils';
import { VersionMessageData } from '../../versionMetadata/MetadataComponents';
import { getBaseMetadata, VersionMetadata } from '../../versionMetadata/VersionMetadata';

interface ICurrentVersionProps {
  data: QueryArtifactVersion;
  environment: string;
  reference: string;
  numNewerVersions?: number;
  pinned?: VersionMessageData;
}

export const CurrentVersion = ({ data, environment, reference, numNewerVersions, pinned }: ICurrentVersionProps) => {
  const { gitMetadata, constraints, verifications, postDeploy } = data;
  const actions = useCreateVersionActions({
    environment,
    reference,
    version: data.version,
    buildNumber: data.buildNumber,
    status: data.status,
    commitMessage: gitMetadata?.commitInfo?.message,
    isPinned: Boolean(pinned),
    compareLinks: {
      previous: gitMetadata?.comparisonLinks?.toPreviousVersion,
    },
  });
  return (
    <div className="artifact-current-version">
      {gitMetadata ? <GitLink gitMetadata={gitMetadata} /> : <div>Build {data?.version}</div>}
      <VersionMetadata {...getBaseMetadata(data)} buildsBehind={numNewerVersions} actions={actions} pinned={pinned} />
      {constraints && (
        <Constraints constraints={constraints} versionProps={{ environment, reference, version: data.version }} />
      )}
      {verifications && <ArtifactVersionTasks type="Verification" tasks={verifications} />}
      {postDeploy && <ArtifactVersionTasks type="Post deploy" tasks={postDeploy} />}
    </div>
  );
};
