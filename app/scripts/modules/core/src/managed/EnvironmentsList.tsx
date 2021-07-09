import { pickBy, values } from 'lodash';
import React from 'react';

import { ManagedResourceObject } from './ManagedResourceObject';
import { Application } from '../application';
import { IManagedArtifactSummary, IManagedEnvironmentSummary, IManagedResourceSummary } from '../domain';
import { EnvironmentRow } from './environment/EnvironmentRow';
import { resourceManager } from './resources/resourceRegistry';

interface IEnvironmentsListProps {
  application: Application;
  environments: IManagedEnvironmentSummary[];
  resourcesById: { [id: string]: IManagedResourceSummary };
  artifacts: IManagedArtifactSummary[];
}

export function EnvironmentsList({
  application,
  environments,
  resourcesById,
  artifacts: allArtifacts,
}: IEnvironmentsListProps) {
  return (
    <div>
      {environments.map(({ name, resources, artifacts }) => {
        const pinnedVersions = artifacts.filter(({ pinnedVersion }) => pinnedVersion);
        return (
          <EnvironmentRow
            key={name}
            name={name}
            pinnedVersions={pinnedVersions}
            resources={values(pickBy(resourcesById, (resource) => resources.indexOf(resource.id) > -1))}
          >
            {resources
              .map((resourceId) => resourcesById[resourceId])
              .filter((resource) => resourceManager.isSupported(resource.kind))
              .sort((a, b) => `${a.kind}${a.displayName}`.localeCompare(`${b.kind}${b.displayName}`))
              .map((resource) => {
                const artifactVersionsByState =
                  resource.artifact &&
                  artifacts.find(({ reference }) => reference === resource.artifact?.reference)?.versions;
                const artifactDetails =
                  resource.artifact && allArtifacts.find(({ reference }) => reference === resource.artifact?.reference);
                return (
                  <ManagedResourceObject
                    application={application}
                    key={resource.id}
                    resource={resource}
                    metadata={{
                      environment: name,
                      showReferenceName: allArtifacts.length > 1,
                      artifactVersionsByState: artifactVersionsByState,
                      artifactDetails: artifactDetails,
                    }}
                  />
                );
              })}
          </EnvironmentRow>
        );
      })}
    </div>
  );
}
