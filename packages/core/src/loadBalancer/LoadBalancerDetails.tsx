import React from 'react';

import { IOverridableProps, Overridable } from '../overrideRegistry';

export interface ILoadBalancerDetailsProps extends IOverridableProps {}

@Overridable('loadBalancer.details')
export class LoadBalancerDetails extends React.Component<ILoadBalancerDetailsProps> {
  public render() {
    return <h3>Load Balancer Details</h3>;
  }
}
