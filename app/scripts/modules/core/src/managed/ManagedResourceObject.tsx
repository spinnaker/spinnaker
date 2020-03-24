import React from 'react';

import { IManagedResourceSummary, IManagedEnviromentSummary } from '../domain/IManagedEntity';

import { getKindName } from './ManagedReader';
import { ObjectRow } from './ObjectRow';
import { Pill } from './Pill';
import { parseName } from './Frigga';

export interface IManagedResourceObjectProps {
  resource: IManagedResourceSummary;
  artifact?: IManagedEnviromentSummary['artifacts'][0];
}

const kindIconMap: { [key: string]: string } = {
  cluster: 'cluster',
};

function getIconTypeFromKind(kind: string): string {
  return kindIconMap[getKindName(kind)] ?? 'cluster';
}

export const ManagedResourceObject = ({
  resource: {
    kind,
    moniker: { app, stack, detail },
  },
  artifact,
}: IManagedResourceObjectProps) => {
  const { version: currentVersion, buildNumber: currentBuild } = parseName(artifact?.versions.current || '') || {};
  return (
    <ObjectRow
      icon={getIconTypeFromKind(kind)}
      title={[app, stack, detail].filter(Boolean).join('-')}
      metadata={
        artifact?.versions.current && (
          <Pill text={currentBuild ? `#${currentBuild}` : currentVersion || artifact.versions.current || 'unknown'} />
        )
      }
    />
  );
};
