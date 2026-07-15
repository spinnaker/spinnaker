import { UISref } from '@uirouter/react';
import React from 'react';

import type { ILoadBalancerDetailsSectionProps } from '@spinnaker/core';
import { AccountTag, CollapsibleSection, HealthCounts } from '@spinnaker/core';

import { ComponentUrlDetails } from '../../common/ComponentUrlDetails';
import type { ICloudrunLoadBalancer } from '../../common/domain';

export function compareCloudrunLoadBalancerServerGroups(a: any, b: any): number {
  if (a.isDisabled !== b.isDisabled) {
    return a.isDisabled ? 1 : -1;
  }
  return b.name.localeCompare(a.name);
}

export function CloudrunLoadBalancerDetailsSection({ loadBalancer }: ILoadBalancerDetailsSectionProps) {
  const cloudrunLoadBalancer = loadBalancer as ICloudrunLoadBalancer;
  return (
    <CollapsibleSection heading="Load Balancer Details" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>In</dt>
        <dd>
          <AccountTag account={cloudrunLoadBalancer.account} />
        </dd>
        <dt>Region</dt>
        <dd>{cloudrunLoadBalancer.region}</dd>
        {!!cloudrunLoadBalancer.serverGroups.length && <dt>Server Groups</dt>}
        {!!cloudrunLoadBalancer.serverGroups.length && (
          <dd>
            <ul>
              {cloudrunLoadBalancer.serverGroups
                .slice()
                .sort(compareCloudrunLoadBalancerServerGroups)
                .map((serverGroup) => (
                  <li key={serverGroup.name}>
                    <UISref
                      to="^.serverGroup"
                      params={{
                        region: serverGroup.region,
                        accountId: serverGroup.account,
                        serverGroup: serverGroup.name,
                        provider: 'cloudrun',
                      }}
                    >
                      <a>{serverGroup.name}</a>
                    </UISref>
                  </li>
                ))}
            </ul>
          </dd>
        )}
      </dl>
    </CollapsibleSection>
  );
}

export function CloudrunLoadBalancerStatusSection({ loadBalancer }: ILoadBalancerDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Status" defaultExpanded={true}>
      <HealthCounts className="pull-left" container={loadBalancer.instanceCounts} />
    </CollapsibleSection>
  );
}

export function CloudrunLoadBalancerTrafficSplitSection({ loadBalancer }: ILoadBalancerDetailsSectionProps) {
  const cloudrunLoadBalancer = loadBalancer as ICloudrunLoadBalancer;
  return (
    <CollapsibleSection heading="Traffic Split" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <ul>
          {cloudrunLoadBalancer.split?.trafficTargets?.map((trafficTarget) => (
            <li key={trafficTarget.revisionName}>
              {trafficTarget.revisionName}:<span className="pull-right">{trafficTarget.percent}</span>
            </li>
          ))}
        </ul>
      </dl>
    </CollapsibleSection>
  );
}

export function CloudrunLoadBalancerDnsSection({ loadBalancer }: ILoadBalancerDetailsSectionProps) {
  return (
    <CollapsibleSection heading="DNS" defaultExpanded={true}>
      <dl className="dl-narrow">
        <ComponentUrlDetails component={loadBalancer as any} />
      </dl>
    </CollapsibleSection>
  );
}
