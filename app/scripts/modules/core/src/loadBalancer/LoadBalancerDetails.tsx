import * as React from 'react';
import { Overridable, IOverridableProps } from 'core/overrideRegistry';

export interface ILoadBalancerDetailsProps extends IOverridableProps {
}

@Overridable('loadBalancer.details')
export class LoadBalancerDetails extends React.Component<ILoadBalancerDetailsProps> {
  public render() {
    return (
      <h3>Load Balancer Details</h3>
    );
  }
}
