import React from 'react';

import type { IKubernetesServerGroupManagerDetailsSectionProps } from './IKubernetesServerGroupManagerDetailsSectionProps';
import { KubernetesReactInjector } from '../../../reactShims';

export function ServerGroupManagerAnnotationCustomSection({
  serverGroupManager,
  manifest,
}: IKubernetesServerGroupManagerDetailsSectionProps) {
  const { KubernetesAnnotationCustomSections } = KubernetesReactInjector;
  return <KubernetesAnnotationCustomSections manifest={manifest.manifest} resource={serverGroupManager} />;
}
