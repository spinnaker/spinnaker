import React from 'react';

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

function getIconTypeFromKind(kind: string) {
  return kindIconMap[getKindName(kind)] ?? 'placeholder';
}

export const ManagedResourceObject = ({
  resource: {
    kind,
    moniker: { app, stack, detail },
  },
  artifact,
  depth,
}: IManagedResourceObjectProps) => {
  const { version: currentVersion, buildNumber: currentBuild } = parseName(artifact?.versions.current || '') || {};
  return (
    <ObjectRow
      icon={getIconTypeFromKind(kind)}
      title={[app, stack, detail].filter(Boolean).join('-')}
      depth={depth}
      metadata={
        artifact?.versions.current && (
          <Pill text={currentBuild ? `#${currentBuild}` : currentVersion || artifact.versions.current || 'unknown'} />
        )
      }
    />
  );
};
