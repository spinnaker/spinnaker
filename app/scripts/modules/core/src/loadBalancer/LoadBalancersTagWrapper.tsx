import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';

import { Application } from 'core/application/application.model';
import { IServerGroup } from 'core/domain';
import { ReactInjector } from 'core/reactShims';
import { LoadBalancersTag } from './LoadBalancersTag';

export interface ILoadBalancersTagProps {
  application: Application;
  serverGroup: IServerGroup;
};

@autoBindMethods
export class LoadBalancersTagWrapper extends React.Component<ILoadBalancersTagProps, void> {
  public render(): React.ReactElement<LoadBalancersTagWrapper> {
    const { serverGroup } = this.props;
    const { cloudProviderRegistry } = ReactInjector;
    const config = cloudProviderRegistry.getValue(serverGroup.provider || serverGroup.cloudProvider, 'loadBalancer');
    const Tags = (config && config.LoadBalancersTag) || LoadBalancersTag;

    return <Tags {...this.props} />;
  }
}
