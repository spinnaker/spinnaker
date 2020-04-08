import React, { memo } from 'react';
import classNames from 'classnames';

import {
  IManagedArtifactSummary,
  IManagedArtifactVersion,
  IManagedResourceSummary,
  IStatefulConstraint,
  IStatelessConstraint,
} from '../domain';
import { Application } from '../application';
import { useEventListener } from '../presentation';

import { ArtifactDetailHeader } from './ArtifactDetailHeader';
import { ManagedResourceObject } from './ManagedResourceObject';
import { EnvironmentRow } from './EnvironmentRow';
import { VersionStateCard } from './VersionStateCard';

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
  allVersions: IManagedArtifactSummary['versions'];
  resourcesByEnvironment: { [environment: string]: IManagedResourceSummary[] };
  onRequestClose: () => any;
}

export const ArtifactDetail = ({
  application,
  name,
  type,
  version: versionDetails,
  allVersions,
  resourcesByEnvironment,
  onRequestClose,
}: IArtifactDetailProps) => {
  const { version, environments } = versionDetails;

  const keydownCallback = ({ keyCode }: KeyboardEvent) => {
    if (keyCode === 27 /* esc */) {
      onRequestClose();
    }
  };
  useEventListener(document, 'keydown', keydownCallback);

  return (
    <>
      <ArtifactDetailHeader name={name} version={versionDetails} onRequestClose={onRequestClose} />

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
            return (
              <EnvironmentRow
                key={environmentName}
                name={environmentName}
                resources={resourcesByEnvironment[environmentName]}
              >
                <VersionStateCard
                  state={state}
                  deployedAt={deployedAt}
                  replacedAt={replacedAt}
                  replacedBy={replacedBy}
                  allVersions={allVersions}
                />
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
