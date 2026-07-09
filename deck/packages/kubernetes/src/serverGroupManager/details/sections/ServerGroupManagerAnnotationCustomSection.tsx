import React from 'react';

import type { IKubernetesServerGroupManagerDetailsSectionProps } from './IKubernetesServerGroupManagerDetailsSectionProps';
import { AnnotationCustomSections } from '../../../manifest/AnnotationCustomSections';

export function ServerGroupManagerAnnotationCustomSection({
  serverGroupManager,
  manifest,
}: IKubernetesServerGroupManagerDetailsSectionProps) {
  return <AnnotationCustomSections manifest={manifest.manifest} resource={serverGroupManager} />;
}
