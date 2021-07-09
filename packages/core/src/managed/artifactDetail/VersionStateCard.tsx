import { DateTime } from 'luxon';
import React, { memo, useMemo } from 'react';

import { IconNames } from '@spinnaker/presentation';

import { Button } from '../Button';
import { Pill } from '../Pill';
import { IStatusCardProps, StatusCard } from '../StatusCard';
import { getArtifactVersionDisplayName } from '../displayNames';
import { IManagedArtifactVersion } from '../../domain';
import { Markdown } from '../../presentation';

interface CardTitleMetadata {
  deployedAt?: string;
  replacedAt?: string;
  replacedByVersionName?: string;
  vetoed?: IManagedArtifactVersion['environments'][0]['vetoed'];
}

interface CardAppearance {
  icon: IconNames;
  appearance: IStatusCardProps['appearance'];
  timestamp?: (metadata: CardTitleMetadata) => DateTime | undefined;
  title: (metadata: CardTitleMetadata) => string | JSX.Element;
  description?: (metadata: CardTitleMetadata) => string | JSX.Element | undefined;
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
      <span>
        <span>Skipped</span> <span className="text-regular">—</span>{' '}
        {replacedByVersionName && <Pill text={replacedByVersionName} />}{' '}
        <span className="text-regular">{!replacedByVersionName && 'a later version '}became available</span>
      </span>
    ),
  },
  previous: {
    icon: 'cloudDecommissioned',
    appearance: 'archived',
    timestamp: ({ replacedAt }: CardTitleMetadata) => (replacedAt ? DateTime.fromISO(replacedAt) : undefined),
    title: ({ replacedByVersionName }: CardTitleMetadata) => (
      <span>
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
      <span>
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
    timestamp: ({ deployedAt }: CardTitleMetadata) => (deployedAt ? DateTime.fromISO(deployedAt) : undefined),
    title: (_: CardTitleMetadata) => <span>Deployed</span>,
  },
  vetoed: {
    icon: 'artifactBad',
    appearance: 'error',
    timestamp: ({ vetoed }: CardTitleMetadata) =>
      // we have to tolerate some older vetoes (from before June 2020) in the DB that don't have times/user attribution
      !!vetoed ? DateTime.fromISO(vetoed.at) : undefined,
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
    description: ({ vetoed }: CardTitleMetadata) =>
      vetoed?.comment ? <Markdown message={vetoed.comment} tag="span" /> : undefined,
  },
} as const;

export type IVersionStateCardProps = Pick<
  IManagedArtifactVersion['environments'][0],
  'state' | 'deployedAt' | 'replacedAt' | 'replacedBy' | 'vetoed' | 'compareLink'
> & { allVersions: IManagedArtifactVersion[]; logClick: (message: string) => void };

export const VersionStateCard = memo(
  ({
    state,
    deployedAt,
    replacedAt,
    replacedBy,
    vetoed,
    compareLink,
    allVersions,
    logClick,
  }: IVersionStateCardProps) => {
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
        actions={
          compareLink && (
            <a
              className="nostyle"
              href={compareLink}
              target="_blank"
              rel="noopener noreferrer"
              onClick={() => {
                logClick('See changes clicked');
              }}
            >
              <Button>See changes</Button>
            </a>
          )
        }
      />
    );
  },
);
