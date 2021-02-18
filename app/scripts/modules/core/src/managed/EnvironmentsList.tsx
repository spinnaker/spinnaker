import { Application } from 'core/application';
import { pickBy, values } from 'lodash';
import React from 'react';

import { ManagedResourceObject } from './ManagedResourceObject';
import { IManagedArtifactSummary, IManagedEnvironmentSummary, IManagedResourceSummary } from '../domain';
import { EnvironmentRow } from './environment/EnvironmentRow';
import { isResourceKindSupported } from './resources/resourceRegistry';

function shouldDisplayResource(resource: IManagedResourceSummary) {
  return isResourceKindSupported(resource.kind);
}

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
              .filter(shouldDisplayResource)
              .sort((a, b) => `${a.kind}${a.displayName}`.localeCompare(`${b.kind}${b.displayName}`))
              .map((resource) => {
                const artifactVersionsByState =
                  resource.artifact &&
                  artifacts.find(({ reference }) => reference === resource.artifact.reference)?.versions;
                const artifactDetails =
                  resource.artifact && allArtifacts.find(({ reference }) => reference === resource.artifact.reference);
                return (
                  <ManagedResourceObject
                    application={application}
                    key={resource.id}
                    resource={resource}
                    environment={name}
                    showReferenceName={allArtifacts.length > 1}
                    artifactVersionsByState={artifactVersionsByState}
                    artifactDetails={artifactDetails}
                    depth={0}
                  />
                );
              })}
          </EnvironmentRow>
        );
      })}
    </div>
  );
}
