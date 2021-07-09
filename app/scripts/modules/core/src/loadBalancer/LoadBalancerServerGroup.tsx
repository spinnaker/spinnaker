import { UISref, UISrefActive } from '@uirouter/react';
import classNames from 'classnames';
import { clone } from 'lodash';
import React from 'react';

import { CloudProviderLogo } from '../cloudProvider/CloudProviderLogo';
import { IInstance, IServerGroup } from '../domain';
import { HealthCounts } from '../healthCounts/HealthCounts';
import { Instances } from '../instance/Instances';

export interface ILoadBalancerServerGroupProps {
  region: string;
  account: string;
  serverGroup: IServerGroup;
  showInstances: boolean; // boolean on sortFilter, but shouldn't be handled here
}

export interface ILoadBalancerServerGroupState {
  instances: IInstance[];
}

export class LoadBalancerServerGroup extends React.Component<
  ILoadBalancerServerGroupProps,
  ILoadBalancerServerGroupState
> {
  constructor(props: ILoadBalancerServerGroupProps) {
    super(props);
    this.state = this.getState(props);
  }

  private getState(props: ILoadBalancerServerGroupProps): ILoadBalancerServerGroupState {
    return {
      instances: clone(props.serverGroup.instances),
    };
  }

  public componentWillReceiveProps(nextProps: ILoadBalancerServerGroupProps): void {
    this.setState(this.getState(nextProps));
  }

  public render(): React.ReactElement<LoadBalancerServerGroup> {
    const { serverGroup, showInstances, account, region } = this.props;

    const className = classNames({
      clickable: true,
      'clickable-row': true,
      'no-margin-y': true,
      disabled: serverGroup.isDisabled,
    });

    const params = {
      region: serverGroup.region || region,
      accountId: serverGroup.account || account,
      serverGroup: serverGroup.name,
      provider: serverGroup.cloudProvider,
    };

    return (
      <UISrefActive class="active">
        <UISref to=".serverGroup" params={params}>
          <div className={className}>
            <div className="server-group-title container-fluid no-padding">
              <div className="row">
                <div className="col-md-8">
                  <CloudProviderLogo provider={serverGroup.cloudProvider} height={'14px'} width={'14px'} />{' '}
                  {serverGroup.name}
                </div>
                <div className="col-md-4 text-right">
                  <HealthCounts container={serverGroup.instanceCounts} />
                </div>
              </div>
            </div>
            {showInstances && (
              <div className="instance-list">
                <Instances instances={this.state.instances} />
              </div>
            )}
          </div>
        </UISref>
      </UISrefActive>
    );
  }
}
