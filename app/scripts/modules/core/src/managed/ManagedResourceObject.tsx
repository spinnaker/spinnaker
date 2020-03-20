import React from 'react';

import { IManagedResourceSummary } from '../domain/IManagedEntity';

import { getKindName } from './ManagedReader';
import { ObjectRow } from './ObjectRow';

export interface IManagedResourceObjectProps {
  resource: IManagedResourceSummary;
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
    artifact,
    moniker: { app, stack, detail },
  },
}: IManagedResourceObjectProps) => (
  <ObjectRow
    icon={getIconTypeFromKind(kind)}
    title={[app, stack, detail].filter(Boolean).join('-')}
    metadata={artifact?.versions?.current || 'unknown version'}
  />
);
