import React from 'react';

import { CollapsibleSection } from '@spinnaker/core';

import type { IKubernetesLoadBalancerDetailsSectionProps } from './IKubernetesLoadBalancerDetailsSectionProps';
import { ManifestLabels } from '../../../manifest/ManifestLabels';

export function LoadBalancerLabelsSection({ loadBalancer }: IKubernetesLoadBalancerDetailsSectionProps) {
  const { manifest } = loadBalancer;
  return (
    <CollapsibleSection heading="Labels" defaultExpanded={true}>
      <ManifestLabels manifest={manifest.manifest} />
    </CollapsibleSection>
  );
}
