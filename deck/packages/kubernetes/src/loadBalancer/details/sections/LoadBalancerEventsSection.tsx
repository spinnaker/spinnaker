import React from 'react';

import { CollapsibleSection } from '@spinnaker/core';

import type { IKubernetesLoadBalancerDetailsSectionProps } from './IKubernetesLoadBalancerDetailsSectionProps';
import { ManifestEvents } from '../../../pipelines/stages/deployManifest/manifestStatus/ManifestEvents';

export function LoadBalancerEventsSection({ loadBalancer }: IKubernetesLoadBalancerDetailsSectionProps) {
  const { manifest } = loadBalancer;
  return (
    <CollapsibleSection heading="Events" defaultExpanded={true}>
      <ManifestEvents manifest={manifest} />
    </CollapsibleSection>
  );
}
