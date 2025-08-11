import React from 'react';

import type { IKubernetesServerGroupDetailsSectionProps } from './IKubernetesServerGroupDetailsSectionProps';
import { ManifestStatus } from '../../../manifest/status/ManifestStatus';

export function ServerGroupManifestStatusSection({ serverGroup }: IKubernetesServerGroupDetailsSectionProps) {
  const { manifest } = serverGroup;
  return <ManifestStatus status={manifest.status} />;
}
