import React from 'react';
import { keyBy } from 'lodash';

import { IManagedEnviromentSummary, IManagedResourceSummary, IManagedArtifactSummary } from '../domain/IManagedEntity';
import { getKindName } from './ManagedReader';
import { NoticeCard } from './NoticeCard';
import { ObjectRow } from './ObjectRow';

const kindIconMap: { [key: string]: string } = {
  cluster: 'cluster',
};

function getIconTypeFromKind(kind: string): string {
  return kindIconMap[getKindName(kind)] ?? 'cluster';
}

function shouldDisplayResource(resource: IManagedResourceSummary) {
  //TODO: naively filter on presence of moniker but how should we really decide what to display?
  return !!resource.moniker;
}

interface IEnvironmentsListProps {
  environments: IManagedEnviromentSummary[];
  resources: IManagedResourceSummary[];
  artifacts: IManagedArtifactSummary[];
}

export function EnvironmentsList({ environments, resources, artifacts }: IEnvironmentsListProps) {
  const resourcesMap = keyBy(resources, 'id');

  return (
    <div>
      <NoticeCard
        icon="search"
        text={undefined}
        title={`${artifacts.length} artifacts ${
          artifacts.length === 1 ? 'is' : 'are'
        } deployed in 2 environments with no issues detected.`}
        isActive={true}
        noticeType={'ok'}
      />
      {environments.map(({ name, resources }) => (
        <div key={name}>
          <h3>{name.toUpperCase()}</h3>
          {resources
            .map(resourceId => resourcesMap[resourceId])
            .filter(shouldDisplayResource)
            .map(({ id, kind, artifact, moniker: { app, stack, detail } }: IManagedResourceSummary) => (
              <ObjectRow
                key={id}
                icon={getIconTypeFromKind(kind)}
                title={`${[app, stack, detail].filter(Boolean).join('-')} ${artifact?.versions?.current ||
                  'unknown version'}`}
              />
            ))}
        </div>
      ))}
    </div>
  );
}
