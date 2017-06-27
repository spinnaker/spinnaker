import * as React from 'react';
import * as classNames from 'classnames';
import autoBindMethods from 'class-autobind-decorator';
import { clone } from 'lodash';
import { UISref, UISrefActive } from '@uirouter/react';

import { NgReact } from 'core/reactShims';
import { IServerGroup, IInstance } from 'core/domain';

import { CloudProviderLogo } from 'core/cloudProvider/CloudProviderLogo';
import { HealthCounts } from 'core/healthCounts/HealthCounts';

export interface ILoadBalancerServerGroupProps {
  cloudProvider: string;
  region: string;
  account: string;
  serverGroup: IServerGroup;
  showInstances: boolean; // boolean on sortFilter, but shouldn't be handled here
}

export interface ILoadBalancerServerGroupState {
  instances: IInstance[];
}

@autoBindMethods
export class LoadBalancerServerGroup extends React.Component<ILoadBalancerServerGroupProps, ILoadBalancerServerGroupState> {
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
    const { cloudProvider, serverGroup, showInstances, account, region } = this.props;
    const { Instances } = NgReact;

    const className = classNames({
      clickable: true,
      'clickable-row': true,
      'no-margin-top': true,
      disabled: serverGroup.isDisabled,
    });

    const params = {
      region: serverGroup.region || region,
      accountId: account,
      serverGroup: serverGroup.name,
      provider: cloudProvider
    };

    return (
      <UISrefActive class="active">
        <UISref to=".serverGroup" params={params}>
          <div className={className} >
            <div className="server-group-title container-fluid no-padding">
              <div className="row">
                <div className="col-md-8">
                  <CloudProviderLogo provider={cloudProvider} height={'14px'} width={'14px'}/> {serverGroup.name}
                </div>
                <div className="col-md-4 text-right">
                  <HealthCounts container={serverGroup.instanceCounts}/>
                </div>
              </div>
            </div>
            { showInstances && (
              <div className="instance-list">
                <Instances instances={this.state.instances}/>
              </div>
            )}
          </div>
        </UISref>
      </UISrefActive>
    );
  }
}
