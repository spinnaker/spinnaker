import React from 'react';
import { AccountTag, CollapsibleSection, timestamp } from '@spinnaker/core';
import type { IKubernetesLoadBalancerDetailsSectionProps } from './IKubernetesLoadBalancerDetailsSectionProps';

export function LoadBalancerInformationSection({ loadBalancer }: IKubernetesLoadBalancerDetailsSectionProps) {
  const { manifest } = loadBalancer;
  const spec = manifest?.manifest?.spec ?? {};
  return (
    <CollapsibleSection heading="Information" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Created</dt>
        <dd>{timestamp(loadBalancer.createdTime)}</dd>
        <dt>Account</dt>
        <dd>
          <AccountTag account={loadBalancer.account} />
        </dd>
        <dt>Namespace</dt>
        <dd>{loadBalancer.namespace}</dd>
        <dt>Kind</dt>
        <dd>{loadBalancer.kind}</dd>
        <dt>Service Type</dt>
        <dd>{spec.type || '-'}</dd>
        <dt>Sess. Affinity</dt>
        <dd>{spec.sessionAffinity || '-'}</dd>
      </dl>
    </CollapsibleSection>
  );
}
