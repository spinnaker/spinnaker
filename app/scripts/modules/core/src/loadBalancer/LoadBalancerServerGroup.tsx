import * as React from 'react';
import * as $ from 'jquery';
import * as classNames from 'classnames';
import autoBindMethods from 'class-autobind-decorator';
import { clone, last, get } from 'lodash';
import { PathNode } from 'angular-ui-router';
import { Subscription } from 'rxjs';

import { NgReact, ReactInjector } from 'core/reactShims';
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
  active: boolean;
}

@autoBindMethods
export class LoadBalancerServerGroup extends React.Component<ILoadBalancerServerGroupProps, ILoadBalancerServerGroupState> {
  private baseRef: string;
  private stateChangeListener: Subscription;

  constructor(props: ILoadBalancerServerGroupProps) {
    super(props);
    this.state = this.getState(props);

    const { stateEvents } = ReactInjector;

    this.stateChangeListener = stateEvents.stateChangeSuccess.subscribe(
      () => {
        const active = this.isActive();
        if (this.state.active !== active) {
          this.setState({active});
        }
      }
    );
  }

  private isActive(): boolean {
    const { cloudProvider, serverGroup } = this.props;
    return ReactInjector.$state.includes('**.serverGroup', {region: serverGroup.region, accountId: serverGroup.account, serverGroup: serverGroup.name, provider: cloudProvider});
  }


  private handleServerGroupClicked(event: React.MouseEvent<HTMLElement>): void {
    event.stopPropagation();
    const { serverGroup, region, account, cloudProvider } = this.props;
    const params = {
      region: serverGroup.region || region,
      accountId: account,
      serverGroup: serverGroup.name,
      provider: cloudProvider
    };
    ReactInjector.$state.go('.serverGroup', params, { relative: this.baseRef, inherit: true });
  }

  private getState(props: ILoadBalancerServerGroupProps): ILoadBalancerServerGroupState {
    return {
      instances: clone(props.serverGroup.instances),
      active: this.isActive()
    };
  }

  private refCallback(element: HTMLElement): void {
    this.baseRef = this.baseRef || last(get<PathNode[]>($(element).parent().inheritedData('$uiView'), '$cfg.path')).state.name;
  }

  public componentWillReceiveProps(nextProps: ILoadBalancerServerGroupProps): void {
    this.setState(this.getState(nextProps));
  }

  public componentWillUnmount(): void {
    this.stateChangeListener.unsubscribe();
  }

  public render(): React.ReactElement<LoadBalancerServerGroup> {
    const { cloudProvider, serverGroup, showInstances } = this.props;
    const { Instances } = NgReact;

    const className = classNames({
      clickable: true,
      'clickable-row': true,
      'no-margin-top': true,
      disabled: serverGroup.isDisabled,
      active: this.state.active
    });

    return (
      <div
        className={className}
        onClick={this.handleServerGroupClicked}
        ref={this.refCallback}
      >
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
    );
  }
}
