import { orderBy } from 'lodash';
import React from 'react';

import { CurrentVersion } from './CurrentVersion';
import { PendingVersions } from './PendingVersion';
import { EnvironmentItem } from '../../environmentBaseElements/EnvironmentItem';
import { HoverablePopover, Markdown } from '../../../presentation';
import { QueryArtifact, QueryArtifactVersion } from '../types';
import { tooltipShowHideProps } from '../../utils/defaults';
import { toPinnedMetadata } from '../../versionMetadata/MetadataComponents';

import './Artifact.less';

type RequiredKeys<T, K extends keyof T> = Exclude<T, K> & Required<Pick<T, K>>;

const hasCreatedAt = (version?: QueryArtifactVersion): version is RequiredKeys<QueryArtifactVersion, 'createdAt'> => {
  return Boolean(version?.createdAt);
};

const sortVersions = (versions: QueryArtifact['versions']) => {
  return orderBy(versions || [], (version) => (version.createdAt ? new Date(version.createdAt).getTime() : 0), [
    'desc',
  ]);
};

const filterPendingVersions = (versions: QueryArtifact['versions'], currentVersion?: QueryArtifactVersion) => {
  if (!hasCreatedAt(currentVersion)) {
    // Everything is newer than current
    return sortVersions(versions);
  }
  const currentVersionCreatedAt = new Date(currentVersion.createdAt);
  const newerVersions = versions
    ?.filter(hasCreatedAt)
    ?.filter((version) => new Date(version.createdAt) > currentVersionCreatedAt || version.status === 'DEPLOYING');

  return sortVersions(newerVersions);
};

export const PinnedVersion = ({ version }: { version: NonNullable<QueryArtifact['pinnedVersion']> }) => {
  const commitMessage = version.gitMetadata?.commitInfo?.message;
  const build = `#${version.buildNumber}`;
  return (
    <div className="another-version-pinned-warning">
      <i className="fas fa-exclamation-triangle" /> Version{' '}
      {commitMessage ? (
        <HoverablePopover
          {...tooltipShowHideProps}
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
    <EnvironmentItem
      iconName="artifact"
      iconTooltip={`Artifact - ${artifact.type}`}
      className="Artifact"
      title={artifact.reference}
    >
      <div className="artifact-versions-title sp-margin-m-top">Current version</div>
      {currentVersion ? (
        <CurrentVersion
          data={currentVersion}
          environment={artifact.environment}
          reference={artifact.reference}
          numNewerVersions={newerVersions?.length}
          pinned={pinnedVersion?.version === currentVersion.version ? toPinnedMetadata(pinnedVersion) : undefined}
        />
      ) : (
        <div>No version is deployed</div>
      )}
      {pinnedVersion && pinnedVersion.buildNumber !== currentVersion?.buildNumber && (
        <PinnedVersion version={pinnedVersion} />
      )}
      <PendingVersions artifact={artifact} pendingVersions={newerVersions} />
    </EnvironmentItem>
  );
};
