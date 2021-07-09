import React from 'react';

import { CollapsibleSection } from '@spinnaker/core';
import { ICloudFoundryLoadBalancer } from '../../../domain';

export interface ICloudFoundryLoadBalancerLinksSectionProps {
  loadBalancer: ICloudFoundryLoadBalancer;
}

export class CloudFoundryLoadBalancerLinksSection extends React.Component<ICloudFoundryLoadBalancerLinksSectionProps> {
  constructor(props: ICloudFoundryLoadBalancerLinksSectionProps) {
    super(props);
  }

  public render(): JSX.Element {
    const { loadBalancer } = this.props;
    return (
      <>
        <CollapsibleSection heading="Application links" defaultExpanded={true}>
          <dl className="dl-horizontal dl-narrow">
            <dt>HTTP</dt>
            <dd>
              <a href={'http://' + loadBalancer.name}>{'http://' + loadBalancer.name}</a>
            </dd>
            <dt>HTTPS</dt>
            <dd>
              <a href={'https://' + loadBalancer.name}>{'https://' + loadBalancer.name}</a>
            </dd>
          </dl>
        </CollapsibleSection>
      </>
    );
  }
}
