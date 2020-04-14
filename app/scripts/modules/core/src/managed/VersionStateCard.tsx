import React, { memo, useMemo } from 'react';
import { DateTime } from 'luxon';

import { relativeTime, timestamp } from '../utils';
import { IManagedArtifactVersion } from '../domain';

import { getArtifactVersionDisplayName } from './displayNames';
import { StatusCard } from './StatusCard';
import { Pill } from './Pill';

interface CardTitleMetadata {
  deployedAtMillis?: number;
  replacedAtMillis?: number;
  replacedByVersionName?: string;
}

const cardAppearanceByState = {
  pending: {
    icon: 'artifactPending',
    appearance: 'inactive',
    title: (_: CardTitleMetadata) => 'Not deployed here yet',
  },
  skipped: {
    icon: 'artifactSkipped',
    appearance: 'inactive',
    title: ({ replacedByVersionName }: CardTitleMetadata) => (
      <span className="sp-group-margin-xs-xaxis">
        <span>Skipped</span> <span className="text-regular">—</span>{' '}
        {replacedByVersionName && <Pill text={replacedByVersionName} />}{' '}
        <span className="text-regular">{!replacedByVersionName && 'a later version '}became available</span>
      </span>
    ),
  },
  previous: {
    icon: 'cloudDecommissioned',
    appearance: 'neutral',
    title: ({ replacedAtMillis, replacedByVersionName }: CardTitleMetadata) => (
      <span className="sp-group-margin-xs-xaxis">
        <span>Decommissioned {replacedAtMillis && relativeTime(replacedAtMillis)}</span>
        {replacedAtMillis && (
          <span className="text-italic text-regular sp-margin-xs-left">({timestamp(replacedAtMillis)})</span>
        )}{' '}
        {replacedByVersionName && (
          <>
            <span className="text-regular">—</span> <span className="text-regular">replaced by </span>
            <Pill text={replacedByVersionName} />
          </>
        )}
      </span>
    ),
  },
  approved: {
    icon: 'artifactApproved',
    appearance: 'info',
    title: (_: CardTitleMetadata) => (
      <span className="sp-group-margin-xs-xaxis">
        <span>Approved</span> <span className="text-regular">—</span>{' '}
        <span className="text-regular">deployment is about to begin</span>
      </span>
    ),
  },
  deploying: {
    icon: 'cloudProgress',
    appearance: 'progress',
    title: (_: CardTitleMetadata) => 'Deploying',
  },
  current: {
    icon: 'cloudDeployed',
    appearance: 'success',
    title: ({ deployedAtMillis }: CardTitleMetadata) => (
      <span>
        Deployed here{' '}
        {deployedAtMillis ? (
          <>
            since {relativeTime(deployedAtMillis)}{' '}
            <span className="text-italic text-regular sp-margin-xs-left">({timestamp(deployedAtMillis)})</span>
          </>
        ) : (
          'now'
        )}
      </span>
    ),
  },
  vetoed: {
    icon: 'cloudError',
    appearance: 'error',
    title: ({ deployedAtMillis }: CardTitleMetadata) => (
      <span className="sp-group-margin-xs-xaxis">
        Marked as bad <span className="text-regular sp-margin-xs-left">—</span>{' '}
        {deployedAtMillis ? (
          <>
            <span className="text-regular">last deployed {relativeTime(deployedAtMillis)}</span>{' '}
            <span className="text-italic text-regular">({timestamp(deployedAtMillis)})</span>
          </>
        ) : (
          <span className="text-regular">never deployed here</span>
        )}
      </span>
    ),
  },
} as const;

export type IVersionStateCardProps = Pick<
  IManagedArtifactVersion['environments'][0],
  'state' | 'deployedAt' | 'replacedAt' | 'replacedBy'
> & { allVersions: IManagedArtifactVersion[] };

export const VersionStateCard = memo(
  ({ state, deployedAt, replacedAt, replacedBy, allVersions }: IVersionStateCardProps) => {
    const deployedAtMillis = deployedAt ? DateTime.fromISO(deployedAt).toMillis() : null;
    const replacedAtMillis = replacedAt ? DateTime.fromISO(replacedAt).toMillis() : null;

    const replacedByVersion = useMemo(() => allVersions.find(({ version }) => version === replacedBy), [
      replacedBy,
      allVersions,
    ]);
    const replacedByVersionName = replacedByVersion ? getArtifactVersionDisplayName(replacedByVersion) : replacedBy;
    return (
      <StatusCard
        appearance={cardAppearanceByState[state].appearance}
        iconName={cardAppearanceByState[state].icon}
        title={cardAppearanceByState[state].title({ deployedAtMillis, replacedAtMillis, replacedByVersionName })}
      />
    );
  },
);
