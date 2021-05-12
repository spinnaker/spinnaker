import { sortBy } from 'lodash';
import React from 'react';

import { HoverablePopover, IconTooltip, Markdown } from 'core/presentation';

import { CurrentVersion } from './CurrentVersion';
import { PendingVersion } from './PendingVersion';
import { QueryArtifact, QueryArtifactVersion } from '../types';
import { TOOLTIP_DELAY } from '../../utils/defaults';

import './Artifact.less';

type RequiredKeys<T, K extends keyof T> = Exclude<T, K> & Required<Pick<T, K>>;

const hasCreatedAt = (version?: QueryArtifactVersion): version is RequiredKeys<QueryArtifactVersion, 'createdAt'> => {
  return Boolean(version?.createdAt);
};

const filterPendingVersions = (versions: QueryArtifact['versions'], currentVersion?: QueryArtifactVersion) => {
  if (!hasCreatedAt(currentVersion)) {
    // Everything is newer than current
    return versions;
  }
  const currentVersionCreatedAt = new Date(currentVersion.createdAt);
  const newerVersions = versions
    ?.filter(hasCreatedAt)
    ?.filter((version) => new Date(version.createdAt) > currentVersionCreatedAt || version.status === 'DEPLOYING');
  // Sort from newest to oldest
  return sortBy(newerVersions || [], (version) => -1 * new Date(version.createdAt).getTime());
};

export const PinnedVersion = ({ version }: { version: NonNullable<QueryArtifact['pinnedVersion']> }) => {
  const commitMessage = version.gitMetadata?.commitInfo?.message;
  const build = `#${version.buildNumber}`;
  return (
    <div className="another-version-pinned-warning">
      <i className="fas fa-exclamation-triangle" /> Version{' '}
      {commitMessage ? (
        <HoverablePopover
          delayHide={TOOLTIP_DELAY}
          placement="top"
          Component={() => <Markdown className="git-commit-tooltip" message={commitMessage} />}
        >
          {build}
        </HoverablePopover>
      ) : (
        build
      )}{' '}
      was pinned and will be deployed shortly
    </div>
  );
};

interface IArtifactProps {
  artifact: QueryArtifact;
}

export const Artifact = ({ artifact }: IArtifactProps) => {
  const currentVersion = artifact.versions?.find((version) => version.status === 'CURRENT');
  const newerVersions = filterPendingVersions(artifact.versions, currentVersion);
  const { pinnedVersion } = artifact;

  return (
    <div className="Artifact environment-row-element">
      <div className="row-icon">
        <IconTooltip
          tooltip={`Artifact - ${artifact.type}`}
          name="artifact"
          color="primary-g1"
          delayShow={TOOLTIP_DELAY}
        />
      </div>
      <div className="row-details">
        <div className="row-title">{artifact.reference}</div>
        <div className="artifact-versions-title sp-margin-m-top">Current version</div>
        {currentVersion ? (
          <CurrentVersion
            data={currentVersion}
            environment={artifact.environment}
            reference={artifact.reference}
            numNewerVersions={newerVersions?.length}
            isPinned={pinnedVersion?.version === currentVersion.version}
          />
        ) : (
          <div>No version is deployed</div>
        )}
        {pinnedVersion && pinnedVersion.buildNumber !== currentVersion?.buildNumber && (
          <PinnedVersion version={pinnedVersion} />
        )}
        {newerVersions?.length ? (
          <section className="artifact-pending-versions">
            <div className="artifact-versions-title">Pending Versions</div>
            <div className="artifact-pending-versions-list">
              {newerVersions?.map((version, index) => (
                <PendingVersion
                  key={version.version}
                  index={index}
                  environment={artifact.environment}
                  reference={artifact.reference}
                  data={version}
                  isPinned={pinnedVersion?.version === version.version}
                />
              ))}
            </div>
          </section>
        ) : undefined}
      </div>
    </div>
  );
};
