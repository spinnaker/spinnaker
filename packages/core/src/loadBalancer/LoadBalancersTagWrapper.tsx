import React from 'react';

import { LoadBalancersTag } from './LoadBalancersTag';
import { Application } from '../application/application.model';
import { CloudProviderRegistry } from '../cloudProvider';
import { IServerGroup } from '../domain';

export interface ILoadBalancersTagProps {
  application: Application;
  serverGroup: IServerGroup;
  // Render popover contents into this container
  container?: JSX.Element | HTMLElement;
}

export class LoadBalancersTagWrapper extends React.Component<ILoadBalancersTagProps> {
  public render(): React.ReactElement<LoadBalancersTagWrapper> {
    const { serverGroup } = this.props;
    const config = CloudProviderRegistry.getValue(serverGroup.provider || serverGroup.cloudProvider, 'loadBalancer');
    const Tags = (config && config.LoadBalancersTag) || LoadBalancersTag;

    return <Tags {...this.props} />;
  }
}
