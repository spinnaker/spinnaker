import React from 'react';

import type { IKubernetesServerGroupDetailsSectionProps } from './IKubernetesServerGroupDetailsSectionProps';
import { AnnotationCustomSections } from '../../../manifest/AnnotationCustomSections';

export function ServerGroupAnnotationCustomSection({ serverGroup }: IKubernetesServerGroupDetailsSectionProps) {
  const { manifest } = serverGroup;

  return <AnnotationCustomSections manifest={manifest.manifest} resource={serverGroup} />;
}
