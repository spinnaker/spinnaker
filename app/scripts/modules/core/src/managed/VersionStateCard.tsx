import React, { memo, useMemo } from 'react';
import { DateTime } from 'luxon';

import { relativeTime, timestamp } from '../utils';
import { IManagedArtifactVersion } from '../domain';
import { Markdown, IconNames } from '../presentation';

import { getArtifactVersionDisplayName } from './displayNames';
import { StatusCard, IStatusCardProps } from './StatusCard';
import { Pill } from './Pill';

interface CardTitleMetadata {
  deployedAtMillis?: number;
  replacedAtMillis?: number;
  replacedByVersionName?: string;
  vetoed?: IManagedArtifactVersion['environments'][0]['vetoed'];
}

interface CardAppearance {
  icon: IconNames;
  appearance: IStatusCardProps['appearance'];
  title: (metadata: CardTitleMetadata) => string | JSX.Element;
  description?: (metadata: CardTitleMetadata) => string | JSX.Element;
}

const cardAppearanceByState: { [state: string]: CardAppearance } = {
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
    appearance: 'archived',
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
            {relativeTime(deployedAtMillis)}{' '}
            <span className="text-italic text-regular sp-margin-xs-left">({timestamp(deployedAtMillis)})</span>
          </>
        ) : (
          'now'
        )}
      </span>
    ),
  },
  vetoed: {
    icon: 'artifactBad',
    appearance: 'error',
    title: ({ vetoed }: CardTitleMetadata) => {
      // we have to tolerate some older vetoes (from before June 2020) in the DB that don't have times/user attribution
      const hasVetoMetadata = !!vetoed;
      const vetoedAtMillis = hasVetoMetadata ? DateTime.fromISO(vetoed.at).toMillis() : null;
      return (
        <span className="sp-group-margin-xs-xaxis">
          Marked as bad {hasVetoMetadata && `here ${relativeTime(vetoedAtMillis)}`}{' '}
          {hasVetoMetadata && (
            <>
              <span className="text-italic text-regular sp-margin-xs-left">({timestamp(vetoedAtMillis)})</span>{' '}
              <span className="text-regular">—</span> <span className="text-regular">by {vetoed.by}</span>
            </>
          )}
        </span>
      );
    },
    description: ({ vetoed }: CardTitleMetadata) => vetoed?.comment && <Markdown message={vetoed.comment} tag="span" />,
  },
} as const;

export type IVersionStateCardProps = Pick<
  IManagedArtifactVersion['environments'][0],
  'state' | 'deployedAt' | 'replacedAt' | 'replacedBy' | 'vetoed'
> & { allVersions: IManagedArtifactVersion[] };

export const VersionStateCard = memo(
  ({ state, deployedAt, replacedAt, replacedBy, vetoed, allVersions }: IVersionStateCardProps) => {
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
        title={cardAppearanceByState[state].title({
          deployedAtMillis,
          replacedAtMillis,
          replacedByVersionName,
          vetoed,
        })}
        description={cardAppearanceByState[state].description?.({
          deployedAtMillis,
          replacedAtMillis,
          replacedByVersionName,
          vetoed,
        })}
      />
    );
  },
);
