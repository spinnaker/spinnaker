import React from 'react';

import { CollapsibleSection, HealthCounts } from '@spinnaker/core';
import { ICloudFoundryLoadBalancer } from '../../../domain';

export interface ICloudFoundryLoadBalancerStatusSectionProps {
  loadBalancer: ICloudFoundryLoadBalancer;
}

export class CloudFoundryLoadBalancerStatusSection extends React.Component<
  ICloudFoundryLoadBalancerStatusSectionProps
> {
  constructor(props: ICloudFoundryLoadBalancerStatusSectionProps) {
    super(props);
  }

  public render(): JSX.Element {
    const { loadBalancer } = this.props;
    return (
      <>
        <CollapsibleSection heading="Status" defaultExpanded={true}>
          <HealthCounts className="pull-left" container={loadBalancer.instanceCounts} />
        </CollapsibleSection>
      </>
    );
  }
}
