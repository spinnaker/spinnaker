import React from 'react';
import { pickBy, values } from 'lodash';

import { Application } from 'core/application';
import { IManagedEnviromentSummary, IManagedResourceSummary, IManagedArtifactSummary } from '../domain';

import { ManagedResourceObject } from './ManagedResourceObject';
import { EnvironmentRow } from './EnvironmentRow';

import { isResourceKindSupported } from './resources/resourceRegistry';

function shouldDisplayResource(resource: IManagedResourceSummary) {
  return isResourceKindSupported(resource.kind);
}

interface IEnvironmentsListProps {
  application: Application;
  environments: IManagedEnviromentSummary[];
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
        const hasPinnedVersions = artifacts.some(({ pinnedVersion }) => pinnedVersion);

        return (
          <EnvironmentRow
            key={name}
            name={name}
            hasPinnedVersions={hasPinnedVersions}
            resources={values(pickBy(resourcesById, resource => resources.indexOf(resource.id) > -1))}
          >
            {resources
              .map(resourceId => resourcesById[resourceId])
              .filter(shouldDisplayResource)
              .map(resource => {
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
                    artifactVersionsByState={artifactVersionsByState}
                    artifactDetails={artifactDetails}
                  />
                );
              })}
          </EnvironmentRow>
        );
      })}
    </div>
  );
}
