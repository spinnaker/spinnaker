import React, { memo, useMemo } from 'react';
import { DateTime } from 'luxon';

import { IManagedArtifactVersion } from '../domain';
import { Markdown, IconNames } from '../presentation';

import { getArtifactVersionDisplayName } from './displayNames';
import { StatusCard, IStatusCardProps } from './StatusCard';
import { Pill } from './Pill';

interface CardTitleMetadata {
  deployedAt?: string;
  replacedAt?: string;
  replacedByVersionName?: string;
  vetoed?: IManagedArtifactVersion['environments'][0]['vetoed'];
}

interface CardAppearance {
  icon: IconNames;
  appearance: IStatusCardProps['appearance'];
  timestamp?: (metadata: CardTitleMetadata) => DateTime;
  title: (metadata: CardTitleMetadata) => string | JSX.Element;
  description?: (metadata: CardTitleMetadata) => string | JSX.Element;
}

const cardAppearanceByState: { [state: string]: CardAppearance } = {
  pending: {
    icon: 'artifactPending',
    appearance: 'future',
    title: (_: CardTitleMetadata) => 'Not deployed yet',
  },
  skipped: {
    icon: 'artifactSkipped',
    appearance: 'future',
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
    timestamp: ({ replacedAt }: CardTitleMetadata) => (replacedAt ? DateTime.fromISO(replacedAt) : null),
    title: ({ replacedByVersionName }: CardTitleMetadata) => (
      <span className="sp-group-margin-xs-xaxis">
        <span>Decommissioned</span>{' '}
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
    timestamp: ({ deployedAt }: CardTitleMetadata) => (deployedAt ? DateTime.fromISO(deployedAt) : null),
    title: (_: CardTitleMetadata) => <span>Deployed</span>,
  },
  vetoed: {
    icon: 'artifactBad',
    appearance: 'error',
    timestamp: ({ vetoed }: CardTitleMetadata) =>
      // we have to tolerate some older vetoes (from before June 2020) in the DB that don't have times/user attribution
      !!vetoed ? DateTime.fromISO(vetoed.at) : null,
    title: ({ vetoed }: CardTitleMetadata) => {
      return (
        <span className="sp-group-margin-xs-xaxis">
          <span>Marked as bad</span>{' '}
          {!!vetoed && (
            <>
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
    const replacedByVersion = useMemo(() => allVersions.find(({ version }) => version === replacedBy), [
      replacedBy,
      allVersions,
    ]);
    const replacedByVersionName = replacedByVersion ? getArtifactVersionDisplayName(replacedByVersion) : replacedBy;
    const cardMetadata: CardTitleMetadata = {
      deployedAt,
      replacedAt,
      replacedByVersionName,
      vetoed,
    };

    return (
      <StatusCard
        appearance={cardAppearanceByState[state].appearance}
        background={true}
        iconName={cardAppearanceByState[state].icon}
        timestamp={cardAppearanceByState[state].timestamp?.(cardMetadata)}
        title={cardAppearanceByState[state].title(cardMetadata)}
        description={cardAppearanceByState[state].description?.(cardMetadata)}
      />
    );
  },
);
