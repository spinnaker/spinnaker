import { isEmpty } from 'lodash';
import { DateTime } from 'luxon';
import React from 'react';

import { Constraints } from './Constraints';
import { GitLink } from './GitLink';
import { RelativeTimestamp } from '../../RelativeTimestamp';
import { QueryArtifact, QueryArtifactVersion } from '../types';
import { getLifecycleEventDuration, getLifecycleEventLink, useCreateVersionActions } from './utils';
import { TOOLTIP_DELAY } from '../../utils/defaults';
import { VersionMetadata } from '../../versionMetadata/VersionMetadata';

export interface IPendingVersionsProps {
  artifact: QueryArtifact;
  pendingVersions?: QueryArtifactVersion[];
}

const NUM_VERSIONS_WHEN_COLLAPSED = 2;

export const PendingVersions = ({ artifact, pendingVersions }: IPendingVersionsProps) => {
  const numVersions = pendingVersions?.length || 0;
  const [isExpanded, setIsExpanded] = React.useState(false);

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
            isPinned={pinnedVersion?.version === version.version}
          />
        ))}
        {numVersions > NUM_VERSIONS_WHEN_COLLAPSED ? (
          <div className="artifact-pending-version">
            <button
              type="button"
              className="btn btn-link show-more-versions"
              onClick={() => setIsExpanded((state) => !state)}
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
  isPinned: boolean;
  index: number;
}

const PendingVersion = ({ data, reference, environment, isPinned, index }: IPendingVersionProps) => {
  const { buildNumber, version, gitMetadata, constraints, status } = data;
  const actions = useCreateVersionActions({
    environment,
    reference,
    buildNumber,
    version,
    commitMessage: gitMetadata?.commitInfo?.message,
    isPinned,
    compareLinks: {
      current: gitMetadata?.comparisonLinks?.toCurrentVersion,
    },
  });

  return (
    <div className="artifact-pending-version">
      {data.createdAt && (
        <div className="artifact-pending-version-timestamp">
          <RelativeTimestamp timestamp={DateTime.fromISO(data.createdAt)} delayShow={TOOLTIP_DELAY} />
        </div>
      )}
      <div className="artifact-pending-version-commit">
        {gitMetadata ? <GitLink gitMetadata={gitMetadata} /> : `Build ${buildNumber}`}
      </div>
      <VersionMetadata
        buildNumber={buildNumber}
        buildLink={getLifecycleEventLink(data, 'BUILD')}
        author={gitMetadata?.author}
        buildDuration={getLifecycleEventDuration(data, 'BUILD')}
        isDeploying={status === 'DEPLOYING'}
        isPinned={isPinned}
        actions={actions}
      />
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
