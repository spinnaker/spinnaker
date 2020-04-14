import React from 'react';
import { pickBy, values } from 'lodash';

import { IManagedEnviromentSummary, IManagedResourceSummary, IManagedArtifactSummary } from '../domain';

import { ManagedResourceObject } from './ManagedResourceObject';
import { EnvironmentRow } from './EnvironmentRow';

function shouldDisplayResource(resource: IManagedResourceSummary) {
  //TODO: naively filter on presence of moniker but how should we really decide what to display?
  return !!resource.moniker;
}

interface IEnvironmentsListProps {
  environments: IManagedEnviromentSummary[];
  resourcesById: { [id: string]: IManagedResourceSummary };
  artifacts: IManagedArtifactSummary[];
}

export function EnvironmentsList({ environments, resourcesById, artifacts: allArtifacts }: IEnvironmentsListProps) {
  return (
    <div>
      {environments.map(({ name, resources, artifacts }) => (
        <EnvironmentRow
          key={name}
          name={name}
          resources={values(pickBy(resourcesById, resource => resources.indexOf(resource.id) > -1))}
        >
          {resources
            .map(resourceId => resourcesById[resourceId])
            .filter(shouldDisplayResource)
            .map(resource => {
              const artifactVersionsByState =
                resource.artifact &&
                artifacts.find(({ name, type }) => name === resource.artifact.name && type === resource.artifact.type)
                  ?.versions;
              const artifactDetails =
                resource.artifact &&
                allArtifacts.find(
                  ({ name, type }) => name === resource.artifact.name && type === resource.artifact.type,
                );
              return (
                <ManagedResourceObject
                  key={resource.id}
                  resource={resource}
                  artifactVersionsByState={artifactVersionsByState}
                  artifactDetails={artifactDetails}
                />
              );
            })}
        </EnvironmentRow>
      ))}
    </div>
  );
}
