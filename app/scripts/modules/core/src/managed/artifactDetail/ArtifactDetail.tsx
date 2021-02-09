import React, { memo, useMemo } from 'react';
import ReactGA from 'react-ga';
import classNames from 'classnames';
import { useRouter } from '@uirouter/react';
import { useTransition, animated, UseTransitionProps } from 'react-spring';
import { DateTime } from 'luxon';

import {
  IManagedArtifactSummary,
  IManagedArtifactVersion,
  IManagedEnvironmentSummary,
  IManagedResourceSummary,
  IManagedArtifactVersionEnvironment,
} from '../../domain';
import { Application } from '../../application';
import { useEventListener, Markdown, CollapsibleElement } from '../../presentation';

import { AbsoluteTimestamp } from '../AbsoluteTimestamp';
import { ArtifactDetailHeader } from './ArtifactDetailHeader';
import { ManagedResourceObject } from '../ManagedResourceObject';
import { EnvironmentRow } from '../environment/EnvironmentRow';
import { PreDeploymentRow } from './PreDeploymentRow';
import { PreDeploymentStepCard } from './PreDeploymentStepCard';
import { VersionStateCard } from './VersionStateCard';
import { StatusCard } from '../StatusCard';
import { Button } from '../Button';
import { showPinArtifactModal } from './PinArtifactModal';
import { showUnpinArtifactModal } from './UnpinArtifactModal';
import { showMarkArtifactAsBadModal } from './MarkArtifactAsBadModal';

import { ConstraintCard } from './constraints/ConstraintCard';
import { isConstraintSupported } from './constraints/constraintRegistry';
import { isResourceKindSupported } from '../resources/resourceRegistry';

import './ArtifactDetail.less';

const SUPPORTED_PRE_DEPLOYMENT_TYPES = ['BUILD', 'BAKE'];

function shouldDisplayResource(reference: string, resource: IManagedResourceSummary) {
  return isResourceKindSupported(resource.kind) && reference === resource.artifact?.reference;
}

const logEvent = (label: string, application: string, environment: string, reference: string) =>
  ReactGA.event({
    category: 'Environments - version details',
    action: label,
    label: `${application}:${environment}:${reference}`,
  });

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
    await new Promise((resolve) => setTimeout(resolve, 180));
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
  environment: IManagedArtifactVersionEnvironment;
  pinnedVersion: string;
};

const EnvironmentCards = memo(
  ({
    application,
    environment,
    reference,
    version: versionDetails,
    allVersions,
    pinnedVersion,
    resourcesByEnvironment,
  }: IEnvironmentCardsProps) => {
    const {
      name: environmentName,
      state,
      deployedAt,
      replacedAt,
      replacedBy,
      pinned,
      vetoed,
      statefulConstraints,
      statelessConstraints,
      compareLink,
    } = environment;
    const {
      stateService: { go },
    } = useRouter();

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
      <StatusCard
        iconName="pin"
        appearance="warning"
        background={true}
        timestamp={pinned?.at ? DateTime.fromISO(pinned.at) : null}
        title={
          <span className="sp-group-margin-xs-xaxis">
            <span>Pinned</span> <span className="text-regular">—</span>{' '}
            <span className="text-regular">by {pinned.by}</span>
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
        compareLink={compareLink}
        allVersions={allVersions}
        logClick={(message) => logEvent(message, application.name, environmentName, reference)}
      />
    );
    const constraintCards = useMemo(
      () =>
        [...(statelessConstraints || []), ...(statefulConstraints || [])]
          .filter(({ type }) => isConstraintSupported(type))
          .map((constraint) => (
            <ConstraintCard
              key={constraint.type}
              application={application}
              environment={environment}
              reference={reference}
              version={versionDetails.version}
              constraint={constraint}
            />
          )),
      [application, environmentName, versionDetails.version, statefulConstraints, statelessConstraints],
    );

    const transitions = useTransition(
      [...constraintCards, ...[versionStateCard, pinnedCard, differentVersionPinnedCard].filter(Boolean)],
      ({ key }) => key,
      cardTransitionConfig,
    );

    return (
      <>
        {
          /*
           * Since transitions trail in ascending order, we need to reverse them
           * to get the trail to go up the the list instead of down.
           */
          transitions.reverse().map(({ item: card, key, props }) => (
            <animated.div key={key} style={props}>
              {card}
            </animated.div>
          ))
        }
      </>
    );
  },
);

const VersionMetadataItem = ({ label, value }: { label: string; value: JSX.Element | string }) => (
  <div className="flex-container-h sp-margin-xs-bottom">
    <div className="metadata-label text-bold text-right sp-margin-l-right flex-none">{label}</div>
    <CollapsibleElement maxHeight={150}>{value}</CollapsibleElement>
  </div>
);

export interface IArtifactDetailProps {
  application: Application;
  name: string;
  reference: string;
  version: IManagedArtifactVersion;
  allVersions: IManagedArtifactSummary['versions'];
  allEnvironments: IManagedEnvironmentSummary[];
  showReferenceNames: boolean;
  resourcesByEnvironment: { [environment: string]: IManagedResourceSummary[] };
  onRequestClose: () => any;
}

export const ArtifactDetail = ({
  application,
  reference,
  version: versionDetails,
  allVersions,
  allEnvironments,
  showReferenceNames,
  resourcesByEnvironment,
  onRequestClose,
}: IArtifactDetailProps) => {
  const { environments, lifecycleSteps, git, createdAt } = versionDetails;

  const keydownCallback = ({ keyCode }: KeyboardEvent) => {
    if (keyCode === 27 /* esc */) {
      onRequestClose();
    }
  };
  useEventListener(document, 'keydown', keydownCallback);

  const isPinnedEverywhere = environments.every(({ pinned }) => pinned);
  const isBadEverywhere = environments.every(({ state }) => state === 'vetoed');
  const createdAtTimestamp = useMemo(() => createdAt && DateTime.fromISO(createdAt), [createdAt]);

  // These steps come in with chronological ordering, but we need reverse-chronological orddering for display
  const preDeploymentSteps = lifecycleSteps
    ?.filter(({ scope, type }) => scope === 'PRE_DEPLOYMENT' && SUPPORTED_PRE_DEPLOYMENT_TYPES.includes(type))
    .reverse();

  return (
    <>
      <ArtifactDetailHeader
        reference={showReferenceNames ? reference : null}
        version={versionDetails}
        onRequestClose={onRequestClose}
      />

      <div className="ArtifactDetail flex-grow">
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
            {createdAtTimestamp && (
              <VersionMetadataItem
                label="Created"
                value={<AbsoluteTimestamp timestamp={createdAtTimestamp} clickToCopy={true} />}
              />
            )}
            {git?.author && <VersionMetadataItem label="Author" value={git.author} />}
            {git?.pullRequest?.number && git?.pullRequest?.url && (
              <VersionMetadataItem
                label="Pull Request"
                value={
                  <a
                    href={git.pullRequest.url}
                    onClick={() => logEvent('PR link clicked', application.name, 'none', reference)}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
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
                    <a
                      href={git.commitInfo.link}
                      onClick={() => logEvent('Commit link clicked', application.name, 'none', reference)}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      {git.commitInfo.sha.substring(0, 7)}
                    </a>
                  }
                />
                <VersionMetadataItem
                  label="Message"
                  value={
                    <span style={{ wordBreak: 'break-word', whiteSpace: 'pre-wrap' }}>
                      <Markdown message={git.commitInfo.message} tag="span" />
                    </span>
                  }
                />
              </>
            )}
            {git?.branch && <VersionMetadataItem label="Branch" value={git.branch} />}
            {git?.repo && <VersionMetadataItem label="Repository" value={`${git.project}/${git.repo.name}`} />}
          </div>
        </div>
        {environments.map((environment) => {
          const { name: environmentName, state } = environment;

          const { pinnedVersion } = allEnvironments
            .find(({ name }) => name === environmentName)
            .artifacts.find(({ reference: referenceToMatch }) => referenceToMatch === reference);

          return (
            <EnvironmentRow
              key={environmentName}
              name={environmentName}
              resources={resourcesByEnvironment[environmentName]}
            >
              <div>
                <EnvironmentCards
                  application={application}
                  environment={environment}
                  reference={reference}
                  version={versionDetails}
                  allVersions={allVersions}
                  pinnedVersion={pinnedVersion}
                  resourcesByEnvironment={resourcesByEnvironment}
                />
              </div>
              <div className="sp-margin-l-top">
                {resourcesByEnvironment[environmentName]
                  .filter((resource) => shouldDisplayResource(reference, resource))
                  .sort((a, b) => `${a.kind}${a.displayName}`.localeCompare(`${b.kind}${b.displayName}`))
                  .map((resource) => (
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
        {preDeploymentSteps && preDeploymentSteps.length > 0 && (
          <PreDeploymentRow>
            {preDeploymentSteps.map((step) => (
              <PreDeploymentStepCard key={step.id} step={step} application={application} reference={reference} />
            ))}
          </PreDeploymentRow>
        )}
      </div>
    </>
  );
};
