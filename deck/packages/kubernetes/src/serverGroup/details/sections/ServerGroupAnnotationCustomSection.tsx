import React from 'react';

import type { IKubernetesServerGroupDetailsSectionProps } from './IKubernetesServerGroupDetailsSectionProps';
import { KubernetesReactInjector } from '../../../reactShims';

export function ServerGroupAnnotationCustomSection({ serverGroup }: IKubernetesServerGroupDetailsSectionProps) {
  const { manifest } = serverGroup;
  const { KubernetesAnnotationCustomSections } = KubernetesReactInjector;

  return <KubernetesAnnotationCustomSections manifest={manifest.manifest} resource={serverGroup} />;
}
