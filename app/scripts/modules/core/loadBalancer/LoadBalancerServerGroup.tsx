import * as React from 'react';
import * as $ from 'jquery';
import autoBindMethods from 'class-autobind-decorator';
import { clone, last, get } from 'lodash';
import { PathNode } from 'angular-ui-router';

import { $state } from 'core/uirouter';
import { ILoadBalancer, IServerGroup, IInstance } from 'core/domain';

import { CloudProviderLogo } from 'core/cloudProvider/CloudProviderLogo';
import { HealthCounts } from 'core/healthCounts/HealthCounts';
import { Instances } from 'core/instance/Instances';

interface IProps {
  loadBalancer: ILoadBalancer;
  serverGroup: IServerGroup;
  showInstances: boolean; // boolean on sortFilter, but shouldn't be handled here
}

interface IState {
  instances: IInstance[];
}

@autoBindMethods
export class LoadBalancerServerGroup extends React.Component<IProps, IState> {
  private baseRef: string;

  constructor(props: IProps) {
    super(props);
    this.state = this.getState(props);
  }

  private handleServerGroupClicked(event: React.MouseEvent<HTMLElement>): void {
    event.stopPropagation();
    const { serverGroup, loadBalancer } = this.props;
    const params = {
      region: serverGroup.region || loadBalancer.region,
      accountId: loadBalancer.account,
      serverGroup: serverGroup.name,
      provider: loadBalancer.cloudProvider
    };
    $state.go('.serverGroup', params, { relative: this.baseRef, inherit: true });
  }

  private getState(props: IProps): IState {
    return {
      instances: clone(props.serverGroup.instances)
    };
  }

  private refCallback(element: HTMLElement): void {
    this.baseRef = this.baseRef || last(get<PathNode[]>($(element).parent().inheritedData('$uiView'), '$cfg.path')).state.name;
  }

  public componentWillReceiveProps(nextProps: IProps): void {
    this.setState(this.getState(nextProps));
  }

  public render(): React.ReactElement<LoadBalancerServerGroup> {
    const { loadBalancer, serverGroup, showInstances } = this.props;

    return (
      <div className={`cluster-container ${serverGroup.isDisabled ? 'disabled' : ''}`} ref={this.refCallback}>
        <div className="server-group-title">
          <div className="container-fluid no-padding">
            <div className="row">
              <div className="col-md-8">
                <a onClick={this.handleServerGroupClicked}>
                  <CloudProviderLogo provider={loadBalancer.cloudProvider} height={'14px'} width={'14px'}/> {serverGroup.name}
                </a>
              </div>
              <div className="col-md-4 text-right">
                <HealthCounts container={serverGroup.instanceCounts}/>
              </div>
            </div>
          </div>
        </div>
        { showInstances && (
          <div className="instance-list">
            <Instances instances={this.state.instances}/>
          </div>
        )}
      </div>
    );
  }
}
