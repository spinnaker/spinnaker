import React from 'react';

import { IManagedEnviromentSummary, IManagedResourceSummary, IManagedArtifactSummary } from '../domain';

import { NoticeCard } from './NoticeCard';
import { ManagedResourceObject } from './ManagedResourceObject';

function shouldDisplayResource(resource: IManagedResourceSummary) {
  //TODO: naively filter on presence of moniker but how should we really decide what to display?
  return !!resource.moniker;
}

interface IEnvironmentsListProps {
  environments: IManagedEnviromentSummary[];
  resourcesById: { [id: string]: IManagedResourceSummary };
  artifacts: IManagedArtifactSummary[];
}

export function EnvironmentsList({ environments, resourcesById, artifacts }: IEnvironmentsListProps) {
  return (
    <div>
      <NoticeCard
        icon="search"
        text={undefined}
        title={`${artifacts.length} ${
          artifacts.length === 1 ? 'artifact is' : 'artifacts are'
        } deployed in 2 environments with no issues detected.`}
        isActive={true}
        noticeType={'ok'}
      />
      {environments.map(({ name, resources }) => (
        <div key={name}>
          <h3>{name.toUpperCase()}</h3>
          {resources
            .map(resourceId => resourcesById[resourceId])
            .filter(shouldDisplayResource)
            .map(resource => (
              <ManagedResourceObject key={resource.id} resource={resource} />
            ))}
        </div>
      ))}
    </div>
  );
}
