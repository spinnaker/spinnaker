import { useRouter } from '@uirouter/react';
import React from 'react';

import { IArtifactDetailProps } from './ArtifactDetail';
import { Button } from '../Button';
import { PinnedCard } from './PinnedCard';
import { StatusCard } from '../StatusCard';
import { VersionStateCard } from './VersionStateCard';
import { ConstraintCard } from '../constraints/ConstraintCard';
import { IManagedArtifactVersionEnvironment } from '../../domain';
import { logCategories, useLogEvent } from '../utils/logging';
import { VerificationCard } from './verifications/VerificationCard';

interface IEnvironmentCardsProps
  extends Pick<
    IArtifactDetailProps,
    'application' | 'reference' | 'version' | 'allVersions' | 'resourcesByEnvironment'
  > {
  environment: IManagedArtifactVersionEnvironment;
  pinnedVersion?: string;
}

export const EnvironmentCards: React.FC<IEnvironmentCardsProps> = ({
  application,
  environment,
  reference,
  version: versionDetails,
  allVersions,
  pinnedVersion,
  resourcesByEnvironment,
}) => {
  const {
    name: environmentName,
    state,
    deployedAt,
    replacedAt,
    replacedBy,
    pinned,
    vetoed,
    constraints,
    compareLink,
  } = environment;
  const {
    stateService: { go },
  } = useRouter();

  const logEvent = useLogEvent(logCategories.artifactDetails);

  const differentVersionPinnedCard = pinnedVersion &&
    pinnedVersion !== versionDetails.version &&
    !['vetoed', 'skipped'].includes(state) && (
      <StatusCard
        iconName="cloudWaiting"
        appearance="warning"
        background={true}
        title="A different version is pinned here"
        actions={<Button onClick={() => go('.', { version: pinnedVersion })}>See version</Button>}
      />
    );

  const pinnedCard = pinned && (
    <PinnedCard
      resourcesByEnvironment={resourcesByEnvironment}
      environmentName={environmentName}
      pinned={pinned}
      reference={reference}
      version={versionDetails}
    />
  );

  return (
    <>
      {differentVersionPinnedCard}
      {pinnedCard}
      <VersionStateCard
        key="versionStateCard"
        state={state}
        deployedAt={deployedAt}
        replacedAt={replacedAt}
        replacedBy={replacedBy}
        vetoed={vetoed}
        compareLink={compareLink}
        allVersions={allVersions}
        logClick={(action) => logEvent({ action, label: `${environmentName}:${reference}` })}
      />
      {environment.verifications?.map((verification) => (
        <VerificationCard
          key={verification.id}
          verification={verification}
          wasHalted={environment.state === 'skipped'}
          logClick={(action) => logEvent({ action, label: `${environmentName}:${reference}` })}
        />
      ))}
      {constraints?.map((constraint) => (
        <ConstraintCard
          key={constraint.type}
          application={application}
          environment={environment}
          reference={reference}
          version={versionDetails.version}
          constraint={constraint}
        />
      ))}
    </>
  );
};
