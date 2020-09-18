import React, { memo, useMemo } from 'react';
import classNames from 'classnames';
import { useTransition, animated, UseTransitionProps } from 'react-spring';
import { DateTime } from 'luxon';

import { relativeTime, timestamp } from '../utils';
import { IManagedArtifactSummary, IManagedArtifactVersion, IManagedResourceSummary } from '../domain';
import { Application } from '../application';
import { useEventListener, Markdown } from '../presentation';

import { ArtifactDetailHeader } from './ArtifactDetailHeader';
import { ManagedResourceObject } from './ManagedResourceObject';
import { EnvironmentRow } from './EnvironmentRow';
import { VersionStateCard } from './VersionStateCard';
import { StatusCard } from './StatusCard';
import { Button } from './Button';
import { showPinArtifactModal } from './PinArtifactModal';
import { showUnpinArtifactModal } from './UnpinArtifactModal';
import { showMarkArtifactAsBadModal } from './MarkArtifactAsBadModal';

import { ConstraintCard } from './constraints/ConstraintCard';
import { isConstraintSupported } from './constraints/constraintRegistry';
import { isResourceKindSupported } from './resources/resourceRegistry';

import './ArtifactDetail.less';

function shouldDisplayResource(reference: string, resource: IManagedResourceSummary) {
  return isResourceKindSupported(resource.kind) && reference === resource.artifact?.reference;
}

const inStyles = {
  opacity: 1,
  transform: 'scale(1.0, 1.0)',
};

const outStyles = {
  opacity: 0,
  transform: 'scale(0.95, 0.95)',
};

const cardTransitionConfig = {
  from: outStyles,
  // KLUDGE: all we're *actually* doing in this scary looking handler
  // is delaying the start of any enter transitions for a fixed time
  // so parent transitions for the overall layout have time to start.
  // Unfortunately today useTransition doesn't support fixed delays
  // without tapping into this promise-based orchestration feature (ew).
  // When react-spring v9 is released, this can be changed
  // to a function that returns { to: inStyles, delay: 180 }
  enter: () => async (next: (_: React.CSSProperties) => any) => {
    await new Promise(resolve => setTimeout(resolve, 180));
    next(inStyles);
  },
  leave: outStyles,
  trail: 40,
  config: { mass: 1, tension: 600, friction: 40 },
} as UseTransitionProps<JSX.Element, React.CSSProperties>;

type IEnvironmentCardsProps = Pick<
  IArtifactDetailProps,
  'application' | 'reference' | 'version' | 'allVersions' | 'resourcesByEnvironment'
> & {
  environment: IManagedArtifactSummary['versions'][0]['environments'][0];
};

const EnvironmentCards = memo(
  ({
    application,
    environment: {
      name: environmentName,
      state,
      deployedAt,
      replacedAt,
      replacedBy,
      pinned,
      vetoed,
      statefulConstraints,
      statelessConstraints,
    },
    reference,
    version: versionDetails,
    allVersions,
    resourcesByEnvironment,
  }: IEnvironmentCardsProps) => {
    const pinnedAtMillis = pinned?.at ? DateTime.fromISO(pinned.at).toMillis() : null;

    const pinnedCard = pinned && (
      <StatusCard
        iconName="pin"
        appearance="warning"
        title={
          <span className="sp-group-margin-xs-xaxis">
            Pinned here {relativeTime(pinnedAtMillis)}{' '}
            <span className="text-italic text-regular sp-margin-xs-left">({timestamp(pinnedAtMillis)})</span>{' '}
            <span className="text-regular">—</span> <span className="text-regular">by {pinned.by}</span>
          </span>
        }
        description={pinned.comment && <Markdown message={pinned.comment} tag="span" />}
        actions={
          <Button
            iconName="unpin"
            onClick={() =>
              showUnpinArtifactModal({
                application,
                reference,
                version: versionDetails,
                resourcesByEnvironment,
                environment: environmentName,
              }).then(({ status }) => status === 'CLOSED' && application.getDataSource('environments').refresh())
            }
          >
            Unpin
          </Button>
        }
      />
    );
    const versionStateCard = (
      <VersionStateCard
        key="versionStateCard"
        state={state}
        deployedAt={deployedAt}
        replacedAt={replacedAt}
        replacedBy={replacedBy}
        vetoed={vetoed}
        allVersions={allVersions}
      />
    );
    const constraintCards = useMemo(
      () =>
        [...(statelessConstraints || []), ...(statefulConstraints || [])]
          .filter(({ type }) => isConstraintSupported(type))
          .map(constraint => (
            <ConstraintCard
              key={constraint.type}
              application={application}
              environment={environmentName}
              reference={reference}
              version={versionDetails.version}
              constraint={constraint}
            />
          )),
      [application, environmentName, versionDetails.version, statefulConstraints, statelessConstraints],
    );

    const transitions = useTransition(
      [...constraintCards, ...[versionStateCard, pinnedCard].filter(Boolean)],
      ({ key }) => key,
      cardTransitionConfig,
    );

    return (
      <>
        {/*
         * Since transitions trail in ascending order, we need to reverse them
         * to get the trail to go up the the list instead of down.
         */
        transitions.reverse().map(({ item: card, key, props }) => (
          <animated.div key={key} className="sp-margin-2xs-bottom" style={props}>
            {card}
          </animated.div>
        ))}
      </>
    );
  },
);

const VersionMetadataItem = ({ label, value }: { label: string; value: JSX.Element | string }) => (
  <div className="flex-container-h sp-margin-xs-bottom">
    <div className="metadata-label text-bold text-right sp-margin-l-right flex-none">{label}</div> <span>{value}</span>
  </div>
);

export interface IArtifactDetailProps {
  application: Application;
  name: string;
  reference: string;
  version: IManagedArtifactVersion;
  allVersions: IManagedArtifactSummary['versions'];
  resourcesByEnvironment: { [environment: string]: IManagedResourceSummary[] };
  onRequestClose: () => any;
}

export const ArtifactDetail = ({
  application,
  name,
  reference,
  version: versionDetails,
  allVersions,
  resourcesByEnvironment,
  onRequestClose,
}: IArtifactDetailProps) => {
  const { environments, git } = versionDetails;

  const keydownCallback = ({ keyCode }: KeyboardEvent) => {
    if (keyCode === 27 /* esc */) {
      onRequestClose();
    }
  };
  useEventListener(document, 'keydown', keydownCallback);

  const isPinnedEverywhere = environments.every(({ pinned }) => pinned);
  const isBadEverywhere = environments.every(({ state }) => state === 'vetoed');

  return (
    <>
      <ArtifactDetailHeader name={name} version={versionDetails} onRequestClose={onRequestClose} />

      <div className="ArtifactDetail">
        <div className="flex-container-h top sp-margin-xl-bottom">
          <div className="flex-container-h sp-group-margin-s-xaxis flex-none">
            <Button
              iconName="pin"
              appearance="primary"
              disabled={isPinnedEverywhere || isBadEverywhere}
              onClick={() =>
                showPinArtifactModal({ application, reference, version: versionDetails, resourcesByEnvironment }).then(
                  ({ status }) => status === 'CLOSED' && application.getDataSource('environments').refresh(),
                )
              }
            >
              Pin...
            </Button>
            <Button
              iconName="artifactBad"
              appearance="primary"
              disabled={isPinnedEverywhere || isBadEverywhere}
              onClick={() =>
                showMarkArtifactAsBadModal({
                  application,
                  reference,
                  version: versionDetails,
                  resourcesByEnvironment,
                }).then(({ status }) => status === 'CLOSED' && application.getDataSource('environments').refresh())
              }
            >
              Mark as bad...
            </Button>
          </div>
          <div className="detail-section-right flex-container-v flex-pull-right sp-margin-l-right">
            {git?.author && <VersionMetadataItem label="Author" value={git.author} />}
            {git?.pullRequest?.number && git?.pullRequest?.url && (
              <VersionMetadataItem
                label="Pull Request"
                value={
                  <a href={git.pullRequest.url} target="_blank" rel="noopener noreferrer">
                    #{git.pullRequest.number}
                  </a>
                }
              />
            )}
            {git?.commitInfo && (
              <>
                <VersionMetadataItem
                  label="Commit"
                  value={
                    <a href={git.commitInfo.link} target="_blank" rel="noopener noreferrer">
                      {git.commitInfo.sha.substring(0, 7)}
                    </a>
                  }
                />
                <VersionMetadataItem label="Message" value={git.commitInfo.message} />
              </>
            )}
            {git?.branch && <VersionMetadataItem label="Branch" value={git.branch} />}
            {git?.repo && <VersionMetadataItem label="Repository" value={`${git.project}/${git.repo.name}`} />}
          </div>
        </div>
        {environments.map(environment => {
          const { name: environmentName, state } = environment;
          return (
            <EnvironmentRow
              key={environmentName}
              name={environmentName}
              resources={resourcesByEnvironment[environmentName]}
            >
              <div className="sp-margin-l-right">
                <EnvironmentCards
                  application={application}
                  environment={environment}
                  reference={reference}
                  version={versionDetails}
                  allVersions={allVersions}
                  resourcesByEnvironment={resourcesByEnvironment}
                />
              </div>
              <div className="sp-margin-l-top">
                {resourcesByEnvironment[environmentName]
                  .filter(resource => shouldDisplayResource(reference, resource))
                  .map(resource => (
                    <div key={resource.id} className="flex-container-h middle">
                      {state === 'deploying' && (
                        <div
                          className={classNames(
                            'resource-badge flex-container-h center middle sp-margin-s-right',
                            state,
                          )}
                        />
                      )}
                      <ManagedResourceObject
                        application={application}
                        key={resource.id}
                        resource={resource}
                        depth={state === 'deploying' ? 0 : 1}
                      />
                    </div>
                  ))}
              </div>
            </EnvironmentRow>
          );
        })}
      </div>
    </>
  );
};
