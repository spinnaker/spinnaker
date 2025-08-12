import React from 'react';

import type { IKubernetesServerGroupManagerDetailsSectionProps } from './IKubernetesServerGroupManagerDetailsSectionProps';
import { ManifestStatus } from '../../../manifest/status/ManifestStatus';

export function ServerGroupManagerManifestStatusSection({
  manifest,
}: IKubernetesServerGroupManagerDetailsSectionProps) {
  return <ManifestStatus status={manifest.status} />;
}
