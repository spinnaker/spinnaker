import { useApolloClient } from '@apollo/client';
import classnames from 'classnames';
import { sortBy, toNumber } from 'lodash';
import React from 'react';

import { getEnvTitle } from '../environmentBaseElements/BaseEnvironment';
import type { FetchVersionQueryVariables, MdArtifactStatusInEnvironment } from '../graphql/graphql-sdk';
import { FetchVersionDocument } from '../graphql/graphql-sdk';
import { GitLink } from '../overview/artifact/GitLink';
import { Icon, Tooltip, useApplicationContextSafe } from '../../presentation';
import type { HistoryArtifactVersion, VersionData } from './types';
import { TOOLTIP_DELAY_SHOW } from '../utils/defaults';
import {
  BaseVersionMetadata,
  MetadataBadge,
  VersionAuthor,
  VersionBranch,
  VersionBuilds,
  VersionCreatedAt,
} from '../versionMetadata/MetadataComponents';

import './VersionsHistory.less';

type VersionStatus = MdArtifactStatusInEnvironment;

const getEnvStatusSummary = (artifacts: HistoryArtifactVersion[]): VersionStatus => {
  // We sort from the newest to the oldest
  const sortedArtifacts = sortBy(artifacts, (artifact) => -1 * toNumber(artifact.buildNumber || 0));

  let status: MdArtifactStatusInEnvironment = 'SKIPPED';
  for (const artifact of sortedArtifacts) {
    switch (artifact.status) {
      case 'CURRENT':
      case 'VETOED':
        return artifact.status;
      case 'APPROVED':
      case 'PENDING':
        if (status !== 'DEPLOYING') {
          status = artifact.status;
        }
        break;
      case 'PREVIOUS':
        if (status === 'SKIPPED') {
          status = artifact.status;
        }
        break;
      case 'DEPLOYING':
        status = artifact.status;
        break;
    }
  }
  return status;
};

const getIsCurrent = (artifacts: HistoryArtifactVersion[]) => {
  return artifacts.some((artifact) => artifact.isCurrent);
};

const statusToClassName: { [key in VersionStatus]: string } = {
  APPROVED: `approved`,
  PENDING: `pending`,
  CURRENT: `current`,
  VETOED: `vetoed`,
  PREVIOUS: `previous`,
  DEPLOYING: `deploying`,
  SKIPPED: 'skipped',
};

const statusToText: { [key in VersionStatus]: string } = {
  APPROVED: `Ready to deploy`,
  PENDING: `Pending`,
  CURRENT: `Currently deployed`,
  VETOED: `Marked as bad`,
  PREVIOUS: `Previously deployed`,
  DEPLOYING: `Deploying`,
  SKIPPED: 'Not deployed',
};

const secondaryStatusToIcon: { [key in VersionStatus]?: string } = {
  VETOED: `💔`,
  DEPLOYING: `🚢`,
};

interface IVersionHeadingProps {
  group: VersionData;
  chevron: JSX.Element;
}

export const VersionHeading = ({ group, chevron }: IVersionHeadingProps) => {
  const gitMetadata = group.gitMetadata;
  const client = useApolloClient();
  const app = useApplicationContextSafe();

  const prefetchData = () => {
    // This function is pre-loading the content of the version and caching it before mounting the VersionContent component
    client.query<any, FetchVersionQueryVariables>({
      query: FetchVersionDocument,
      variables: { appName: app.name, versions: Array.from(group.versions) },
    });
  };
  return (
    <div className="version-heading" onMouseOver={prefetchData}>
      <div className="version-heading-content">
        {gitMetadata ? (
          <GitLink gitMetadata={gitMetadata} asLink={false} />
        ) : (
          <div>Build {Array.from(group.buildNumbers).join(', ')}</div>
        )}
        <BaseVersionMetadata>
          {group.isBaking && <MetadataBadge type="baking" />}
          <VersionAuthor author={gitMetadata?.author} />
          <VersionBuilds builds={Array.from(group.buildNumbers).map((buildNumber) => ({ buildNumber }))} />
          <VersionBranch branch={gitMetadata?.branch} />
          <VersionCreatedAt createdAt={group.createdAt} linkProps={{ sha: group.key }} />
        </BaseVersionMetadata>
        {/* Shows a badge for each environment with the status of the artifacts in it */}
        <div className="version-environments">
          {Object.entries(group.environments)
            .reverse()
            .map(([env, artifacts]) => (
              <EnvironmentBadge key={env} name={env} data={artifacts} />
            ))}
        </div>
      </div>
      <div>{chevron}</div>
    </div>
  );
};

interface IEnvironmentBadgeProps {
  name: string;
  data: VersionData['environments'][keyof VersionData['environments']];
}

const EnvironmentBadge = ({ name, data: { isPreview, versions, gitMetadata, isPinned } }: IEnvironmentBadgeProps) => {
  const statusSummary = getEnvStatusSummary(versions);
  const isCurrent = getIsCurrent(versions);
  const statusClassName = statusToClassName[isCurrent ? 'CURRENT' : statusSummary];
  const statusText = statusToText[statusSummary];

  // In case that the status is different than CURRENT (e.g. when you veto the CURRENT version), we want to show that as well
  const hasSecondaryStatus = Boolean(isCurrent && statusSummary !== 'CURRENT');
  const secondaryIcon = hasSecondaryStatus ? secondaryStatusToIcon[statusSummary] : undefined;
  return (
    <Tooltip
      delayShow={TOOLTIP_DELAY_SHOW}
      value={isCurrent && statusSummary !== 'CURRENT' ? `Current & ${statusText}` : statusText}
    >
      <div
        className={classnames(
          'EnvironmentBadge',
          'horizontal',
          'middle',
          { 'preview-env': isPreview },
          statusClassName,
        )}
      >
        {isPinned && <Icon name="pin" size="16px" className="marker pinned" color="black" />}
        {secondaryIcon && <div className={classnames('marker', statusToClassName[statusSummary])}>{secondaryIcon}</div>}
        {getEnvTitle({ name, gitMetadata, isPreview })}
      </div>
    </Tooltip>
  );
};
