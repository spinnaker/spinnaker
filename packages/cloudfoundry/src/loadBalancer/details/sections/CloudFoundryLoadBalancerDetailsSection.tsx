import { UISref } from '@uirouter/react';
import React from 'react';

import { AccountTag, CollapsibleSection } from '@spinnaker/core';
import { ICloudFoundryLoadBalancer } from '../../../domain';

export interface ICloudFoundryLoadBalancerDetailsSectionProps {
  loadBalancer: ICloudFoundryLoadBalancer;
}

export class CloudFoundryLoadBalancerDetailsSection extends React.Component<
  ICloudFoundryLoadBalancerDetailsSectionProps
> {
  constructor(props: ICloudFoundryLoadBalancerDetailsSectionProps) {
    super(props);
  }

  public render(): JSX.Element {
    const { loadBalancer } = this.props;
    return (
      <>
        <CollapsibleSection heading="Load Balancer Details" defaultExpanded={true}>
          <dl className="dl-horizontal dl-narrow">
            <dt>In</dt>
            <dd>
              <AccountTag account={loadBalancer.account} />
            </dd>
            <dt>Organization</dt>
            <dd>{loadBalancer.space.organization.name}</dd>
            <dt>Space</dt>
            <dd>{loadBalancer.space.name}</dd>
            {loadBalancer.serverGroups && (
              <>
                <dt>Server Groups</dt>
                <dd>
                  <ul>
                    {loadBalancer.serverGroups.map((serverGroup, index) => {
                      return (
                        <li key={index}>
                          <UISref
                            to="^.serverGroup"
                            params={{
                              region: serverGroup.region,
                              accountId: serverGroup.account,
                              serverGroup: serverGroup.name,
                              provider: 'cloudfoundry',
                            }}
                          >
                            <a>{serverGroup.name}</a>
                          </UISref>
                        </li>
                      );
                    })}
                  </ul>
                </dd>
              </>
            )}
          </dl>
        </CollapsibleSection>
      </>
    );
  }
}
