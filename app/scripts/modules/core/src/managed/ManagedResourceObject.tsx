import React from 'react';
import { useSref } from '@uirouter/react';

import { IconNames } from '../presentation';
import { IManagedResourceSummary, IManagedEnviromentSummary } from '../domain/IManagedEntity';

import { getKindName } from './ManagedReader';
import { ObjectRow } from './ObjectRow';
import { Pill } from './Pill';
import { parseName } from './Frigga';

export interface IManagedResourceObjectProps {
  resource: IManagedResourceSummary;
  artifact?: IManagedEnviromentSummary['artifacts'][0];
  depth?: number;
}

const kindIconMap: { [kind: string]: IconNames } = {
  cluster: 'cluster',
  'security-group': 'cluster',
  'classic-load-balancer': 'loadBalancer',
  'application-load-balancer': 'loadBalancer',
};

const getIconTypeFromKind = (kind: string) => kindIconMap[getKindName(kind)] ?? 'placeholder';

const getResourceName = ({ moniker: { app, stack, detail } }: IManagedResourceSummary) =>
  [app, stack, detail].filter(Boolean).join('-');

const getResourceRoutingInfo = (
  resource: IManagedResourceSummary,
): { state: string; params: { [key: string]: string } } | null => {
  const {
    kind,
    locations: { account },
  } = resource;
  const kindName = getKindName(kind);
  const params = {
    acct: account,
    q: getResourceName(resource),
  };

  switch (kindName) {
    case 'cluster':
      return { state: 'home.applications.application.insight.clusters', params };

    case 'security-group':
      return { state: 'home.applications.application.insight.firewalls', params };

    case 'classic-load-balancer':
    case 'application-load-balancer':
      return { state: 'home.applications.application.insight.loadBalancers', params };
  }

  return null;
};

export const ManagedResourceObject = ({ resource, artifact, depth }: IManagedResourceObjectProps) => {
  const { version: currentVersion, buildNumber: currentBuild } = parseName(artifact?.versions.current || '') || {};
  const { kind } = resource;
  const resourceName = getResourceName(resource);
  const routingInfo = getResourceRoutingInfo(resource) ?? { state: '', params: {} };
  const route = useSref(routingInfo.state, routingInfo.params);

  return (
    <ObjectRow
      icon={getIconTypeFromKind(kind)}
      title={route ? <a {...route}>{resourceName}</a> : resourceName}
      depth={depth}
      metadata={
        artifact?.versions.current && (
          <Pill text={currentBuild ? `#${currentBuild}` : currentVersion || artifact.versions.current || 'unknown'} />
        )
      }
    />
  );
};
