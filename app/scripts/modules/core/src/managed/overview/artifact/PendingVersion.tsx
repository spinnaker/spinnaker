import { isEmpty } from 'lodash';
import React from 'react';

import { Constraints } from './Constraints';
import { GitLink } from './GitLink';
import { QueryArtifact, QueryArtifactVersion } from '../types';
import { useCreateVersionActions } from './utils';
import { useLogEvent } from '../../utils/logging';
import { toPinnedMetadata, VersionMessageData } from '../../versionMetadata/MetadataComponents';
import { getBaseMetadata, VersionMetadata } from '../../versionMetadata/VersionMetadata';

export interface IPendingVersionsProps {
  artifact: QueryArtifact;
  pendingVersions?: QueryArtifactVersion[];
}

const NUM_VERSIONS_WHEN_COLLAPSED = 2;

export const PendingVersions = ({ artifact, pendingVersions }: IPendingVersionsProps) => {
  const numVersions = pendingVersions?.length || 0;
  const [isExpanded, setIsExpanded] = React.useState(false);
  const logEvent = useLogEvent('ArtifactPendingVersion');

  if (!pendingVersions || !numVersions) return null;

  const versionsToShow = isExpanded ? pendingVersions : pendingVersions.slice(0, NUM_VERSIONS_WHEN_COLLAPSED);
  const numDeploying = pendingVersions.filter((version) => version.status === 'DEPLOYING').length;
  const { pinnedVersion } = artifact;
  return (
    <section className="artifact-pending-versions">
      <div className="artifact-versions-title">
        {numVersions} Pending Versions {numDeploying > 0 ? `(${numDeploying} deploying)` : ''}
      </div>
      <div className="artifact-pending-versions-list">
        {versionsToShow.map((version, index) => (
          <PendingVersion
            key={version.version}
            index={index}
            environment={artifact.environment}
            reference={artifact.reference}
            data={version}
            pinned={pinnedVersion?.version === version.version ? toPinnedMetadata(pinnedVersion) : undefined}
          />
        ))}
        {numVersions > NUM_VERSIONS_WHEN_COLLAPSED ? (
          <div className="artifact-pending-version">
            <button
              type="button"
              className="btn btn-link show-more-versions"
              onClick={() => {
                setIsExpanded((state) => !state);
                logEvent({ action: isExpanded ? 'ShowLess' : 'ShowMore' });
              }}
            >
              {isExpanded ? 'Hide versions...' : 'Show all versions...'}
            </button>
          </div>
        ) : undefined}
      </div>
    </section>
  );
};

interface IPendingVersionProps {
  data: QueryArtifactVersion;
  reference: string;
  environment: string;
  pinned?: VersionMessageData;
  index: number;
}

const PendingVersion = ({ data, reference, environment, pinned, index }: IPendingVersionProps) => {
  const { buildNumber, version, gitMetadata, constraints, status } = data;
  const actions = useCreateVersionActions({
    environment,
    reference,
    buildNumber,
    version,
    status,
    commitMessage: gitMetadata?.commitInfo?.message,
    isPinned: Boolean(pinned),
    compareLinks: {
      current: gitMetadata?.comparisonLinks?.toCurrentVersion,
    },
  });

  return (
    <div className="artifact-pending-version">
      <div className="artifact-pending-version-commit">
        {gitMetadata ? <GitLink gitMetadata={gitMetadata} /> : `Build ${buildNumber}`}
      </div>
      <VersionMetadata {...getBaseMetadata(data)} pinned={pinned} createdAt={data.createdAt} actions={actions} />
      {constraints && !isEmpty(constraints) && (
        <Constraints
          key={index} // This is needed on refresh if a new version was added
          constraints={constraints}
          versionProps={{ environment, reference, version: data.version }}
          expandedByDefault={index === 0}
        />
      )}
    </div>
  );
};
