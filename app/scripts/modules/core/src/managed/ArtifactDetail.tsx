import React, { memo } from 'react';
import classNames from 'classnames';
import { DateTime } from 'luxon';

import { relativeTime, timestamp } from '../utils';
import { IManagedArtifactVersion, IManagedResourceSummary, IStatefulConstraint, IStatelessConstraint } from '../domain';
import { Application } from '../application';
import { useEventListener } from '../presentation';

import { ArtifactDetailHeader } from './ArtifactDetailHeader';
import { NoticeCard } from './NoticeCard';
import { Pill } from './Pill';
import { ManagedResourceObject } from './ManagedResourceObject';
import { parseName } from './Frigga';
import { EnvironmentRow } from './EnvironmentRow';
import { ConstraintCard, IConstraintCardProps } from './constraints/ConstraintCard';
import { isConstraintSupported } from './constraints/constraintRegistry';

import './ArtifactDetail.less';

function shouldDisplayResource(name: string, type: string, resource: IManagedResourceSummary) {
  //TODO: naively filter on presence of moniker but how should we really decide what to display?
  return !!resource.moniker && name === resource.artifact?.name && type === resource.artifact?.type;
}

const ConstraintCards = memo(
  ({
    constraints,
    application,
    environment,
    version,
  }: Partial<IConstraintCardProps> & { constraints: Array<IStatefulConstraint | IStatelessConstraint> }) => (
    <>
      {constraints
        .filter(({ type }) => isConstraintSupported(type))
        .map(constraint => (
          <ConstraintCard
            key={constraint.type}
            className="sp-margin-l-right"
            application={application}
            environment={environment}
            version={version}
            constraint={constraint}
          />
        ))}
    </>
  ),
);

export interface IArtifactDetailProps {
  application: Application;
  name: string;
  type: string;
  version: IManagedArtifactVersion;
  resourcesByEnvironment: { [environment: string]: IManagedResourceSummary[] };
  onRequestClose: () => any;
}

export const ArtifactDetail = ({
  application,
  name,
  type,
  version: { version, environments },
  resourcesByEnvironment,
  onRequestClose,
}: IArtifactDetailProps) => {
  const keydownCallback = ({ keyCode }: KeyboardEvent) => {
    if (keyCode === 27 /* esc */) {
      onRequestClose();
    }
  };
  useEventListener(document, 'keydown', keydownCallback);

  return (
    <>
      <ArtifactDetailHeader name={name} version={version} onRequestClose={onRequestClose} />

      <div className="ArtifactDetail">
        <div className="flex-container-h">
          {/* a short summary with actions/buttons will live here */}
          <div className="detail-section-right">{/* artifact metadata will live here */}</div>
        </div>
        {environments.map(
          ({
            name: environmentName,
            state,
            deployedAt,
            replacedAt,
            replacedBy,
            statefulConstraints,
            statelessConstraints,
          }) => {
            const deployedAtMillis = DateTime.fromISO(deployedAt).toMillis();
            const replacedAtMillis = DateTime.fromISO(replacedAt).toMillis();
            const { version: replacedByPackageVersion, buildNumber: replacedByBuildNumber } =
              parseName(replacedBy || '') || {};

            return (
              <EnvironmentRow
                key={environmentName}
                name={environmentName}
                resources={resourcesByEnvironment[environmentName]}
              >
                {state === 'deploying' && (
                  <NoticeCard
                    className="sp-margin-l-right"
                    icon="cloudProgress"
                    text={undefined}
                    title="Deploying"
                    isActive={true}
                    noticeType="info"
                  />
                )}
                {state === 'current' && deployedAt && (
                  <NoticeCard
                    className="sp-margin-l-right"
                    icon="cloudDeployed"
                    text={undefined}
                    title={
                      <span>
                        Deployed {relativeTime(deployedAtMillis)}{' '}
                        <span className="text-italic text-regular sp-margin-xs-left">
                          ({timestamp(deployedAtMillis)})
                        </span>
                      </span>
                    }
                    isActive={true}
                    noticeType="success"
                  />
                )}
                {state === 'previous' && (
                  <NoticeCard
                    className="sp-margin-l-right"
                    icon="cloudDecommissioned"
                    text={undefined}
                    title={
                      <span className="sp-group-margin-xs-xaxis">
                        Decommissioned {relativeTime(replacedAtMillis)}{' '}
                        <span className="text-italic text-regular sp-margin-xs-left">
                          ({timestamp(replacedAtMillis)})
                        </span>{' '}
                        <span className="text-regular">—</span> <span className="text-regular">replaced by </span>
                        <Pill
                          text={
                            replacedByBuildNumber ? `#${replacedByBuildNumber}` : replacedByPackageVersion || replacedBy
                          }
                        />
                      </span>
                    }
                    isActive={true}
                    noticeType="neutral"
                  />
                )}
                {state === 'pending' && (
                  <NoticeCard
                    className="sp-margin-l-right"
                    icon="placeholder"
                    text={undefined}
                    title="Not deployed here yet"
                    isActive={true}
                    noticeType="neutral"
                  />
                )}
                {state === 'approved' && (
                  <NoticeCard
                    className="sp-margin-l-right"
                    icon="checkBadge"
                    text={undefined}
                    title={
                      <span className="sp-group-margin-xs-xaxis">
                        <span>Approved</span> <span className="text-regular">—</span>{' '}
                        <span className="text-regular">deployment is about to begin</span>
                      </span>
                    }
                    isActive={true}
                    noticeType="info"
                  />
                )}
                {state === 'skipped' && (
                  <NoticeCard
                    className="sp-margin-l-right"
                    icon="placeholder"
                    text={undefined}
                    title={
                      <span className="sp-group-margin-xs-xaxis">
                        <span>Skipped</span> <span className="text-regular">—</span>{' '}
                        {replacedBy && (
                          <Pill
                            text={
                              replacedByBuildNumber
                                ? `#${replacedByBuildNumber}`
                                : replacedByPackageVersion || replacedBy
                            }
                          />
                        )}{' '}
                        <span className="text-regular">{!replacedBy && 'a later version '}became available</span>
                      </span>
                    }
                    isActive={true}
                    noticeType="neutral"
                  />
                )}
                {state === 'vetoed' && (
                  <NoticeCard
                    className="sp-margin-l-right"
                    icon="cloudError"
                    text={undefined}
                    title={
                      <span className="sp-group-margin-xs-xaxis">
                        Marked as bad <span className="text-regular sp-margin-xs-left">—</span>{' '}
                        {deployedAt ? (
                          <>
                            <span className="text-regular">last deployed {relativeTime(deployedAtMillis)}</span>{' '}
                            <span className="text-italic text-regular">({timestamp(deployedAtMillis)})</span>
                          </>
                        ) : (
                          <span className="text-regular">never deployed here</span>
                        )}
                      </span>
                    }
                    isActive={true}
                    noticeType="error"
                  />
                )}
                {statefulConstraints && (
                  <ConstraintCards
                    constraints={statefulConstraints}
                    application={application}
                    environment={environmentName}
                    version={version}
                  />
                )}
                {statelessConstraints && (
                  <ConstraintCards
                    constraints={statelessConstraints}
                    application={application}
                    environment={environmentName}
                    version={version}
                  />
                )}
                <div className="sp-margin-l-top">
                  {resourcesByEnvironment[environmentName]
                    .filter(resource => shouldDisplayResource(name, type, resource))
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
                          key={resource.id}
                          resource={resource}
                          depth={state === 'deploying' ? 0 : 1}
                        />
                      </div>
                    ))}
                </div>
              </EnvironmentRow>
            );
          },
        )}
      </div>
    </>
  );
};
