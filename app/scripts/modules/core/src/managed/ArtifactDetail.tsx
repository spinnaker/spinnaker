import React from 'react';
import classNames from 'classnames';
import { DateTime } from 'luxon';

import { relativeTime, timestamp } from '../utils';
import { IManagedArtifactVersion, IManagedResourceSummary } from '../domain';
import { Application } from '../application';
import { useEventListener } from '../presentation';

import { ArtifactDetailHeader } from './ArtifactDetailHeader';
import { NoticeCard } from './NoticeCard';
import { Pill } from './Pill';
import { ManagedResourceObject } from './ManagedResourceObject';
import { parseName } from './Frigga';
import { EnvironmentRow } from './EnvironmentRow';
import { ConstraintCard } from './constraints/ConstraintCard';
import { isConstraintSupported } from './constraints/constraintRegistry';

import './ArtifactDetail.less';

function shouldDisplayResource(name: string, type: string, resource: IManagedResourceSummary) {
  //TODO: naively filter on presence of moniker but how should we really decide what to display?
  return !!resource.moniker && name === resource.artifact?.name && type === resource.artifact?.type;
}

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
          ({ name: environmentName, state, deployedAt, replacedAt, replacedBy, statefulConstraints }) => {
            const deployedAtMillis = DateTime.fromISO(deployedAt).toMillis();
            const replacedAtMillis = DateTime.fromISO(replacedAt).toMillis();
            const { version: replacedByPackageVersion, buildNumber: replacedByBuildNumber } =
              parseName(replacedBy || '') || {};

            return (
              <EnvironmentRow key={environmentName} name={environmentName} isProd={true}>
                {statefulConstraints &&
                  statefulConstraints
                    .filter(({ type }) => isConstraintSupported(type))
                    .map(constraint => (
                      <ConstraintCard
                        key={constraint.type}
                        className="sp-margin-l-right"
                        application={application}
                        environment={environmentName}
                        version={version}
                        constraint={constraint}
                      />
                    ))}
                {state === 'deploying' && (
                  <NoticeCard
                    className="sp-margin-l-right sp-margin-l-bottom"
                    icon="cloudProgress"
                    text={undefined}
                    title="Deploying"
                    isActive={true}
                    noticeType="info"
                  />
                )}
                {state === 'current' && deployedAt && (
                  <NoticeCard
                    className="sp-margin-l-right sp-margin-l-bottom"
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
                    className="sp-margin-l-right sp-margin-l-bottom"
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
                    className="sp-margin-l-right sp-margin-l-bottom"
                    icon="placeholder"
                    text={undefined}
                    title="Never deployed here"
                    isActive={true}
                    noticeType="neutral"
                  />
                )}
                {state === 'vetoed' && (
                  <NoticeCard
                    className="sp-margin-l-right sp-margin-l-bottom"
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
              </EnvironmentRow>
            );
          },
        )}
      </div>
    </>
  );
};
