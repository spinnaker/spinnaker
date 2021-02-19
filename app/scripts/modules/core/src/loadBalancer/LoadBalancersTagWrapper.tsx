import React from 'react';

import { Application } from 'core/application/application.model';
import { CloudProviderRegistry } from 'core/cloudProvider';
import { IServerGroup } from 'core/domain';

import { LoadBalancersTag } from './LoadBalancersTag';

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
