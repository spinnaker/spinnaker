import { isEmpty } from 'lodash';
import React from 'react';

import { ArtifactCollapsibleSection } from './ArtifactCollapsibleSection';
import { Constraints } from './Constraints';
import { VersionTitle } from './VersionTitle';
import { ArtifactActions } from '../../artifactActions/ArtifactActions';
import type { QueryArtifact, QueryArtifactVersion } from '../types';
import { useCreateVersionRollbackActions } from './useCreateRollbackActions.hook';
import { extractVersionRollbackDetails, isVersionVetoed } from './utils';
import { useLogEvent } from '../../utils/logging';
import type { VersionMessageData } from '../../versionMetadata/MetadataComponents';
import { toPinnedMetadata } from '../../versionMetadata/MetadataComponents';
import { getBaseMetadata, getVersionCompareLinks, VersionMetadata } from '../../versionMetadata/VersionMetadata';

export interface IPendingVersionsProps {
  artifact: QueryArtifact;
  title: string;
  versions?: QueryArtifactVersion[];
  isDeploying?: boolean;
}

const NUM_VERSIONS_WHEN_COLLAPSED = 1;

export const ArtifactVersions = ({ artifact, versions, title, isDeploying }: IPendingVersionsProps) => {
  const numVersions = versions?.length || 0;
  const [isExpanded, setIsExpanded] = React.useState(false);
  const logEvent = useLogEvent('ArtifactPendingVersion');

  if (!versions || !numVersions) return null;

  const versionsToShow = isExpanded ? versions : versions.slice(0, NUM_VERSIONS_WHEN_COLLAPSED);
  const { pinnedVersion } = artifact;
  return (
    <ArtifactCollapsibleSection
      outerDivClassName="artifact-versions artifact-section"
      heading={title}
      isUpdating={isDeploying}
    >
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
    </ArtifactCollapsibleSection>
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
  const { version, gitMetadata, constraints, isCurrent } = data;
  const actions = useCreateVersionRollbackActions({
    environment,
    reference,
    version,
    isVetoed: isVersionVetoed(data),
    isPinned: Boolean(pinned),
    isCurrent,

    selectedVersion: extractVersionRollbackDetails(data),
  });

  return (
    <div className="artifact-pending-version">
      <div className="artifact-pending-version-commit">
        <VersionTitle gitMetadata={gitMetadata} buildNumber={data?.buildNumber} version={data.version} />
      </div>
      <VersionMetadata {...getBaseMetadata(data)} pinned={pinned} />
      <ArtifactActions
        buildNumber={data?.buildNumber}
        version={data.version}
        actions={actions}
        compareLinks={getVersionCompareLinks(data)}
        className="sp-margin-s-yaxis"
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
