import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';
import { isEqual, get, last } from 'lodash';
import { $timeout } from 'ngimport';
import { PathNode } from 'angular-ui-router';
import { Subscription } from 'rxjs';

import { Application } from 'core/application/application.model';
import { ILoadBalancer, IServerGroup } from 'core/domain';
import { NgReact, ReactInjector } from 'core/reactShims';

import { HealthCounts } from 'core/healthCounts/HealthCounts';
import { LoadBalancerClusterContainer } from './LoadBalancerClusterContainer';
import { Sticky } from 'core/utils/stickyHeader/Sticky';

export interface ILoadBalancerProps {
  application: Application;
  loadBalancer: ILoadBalancer;
  serverGroups: IServerGroup[];
  showServerGroups?: boolean;
  showInstances?: boolean;
}

export interface ILoadBalancerState {
  active: boolean;
}

@autoBindMethods
export class LoadBalancer extends React.Component<ILoadBalancerProps, ILoadBalancerState> {
  public static defaultProps: Partial<ILoadBalancerProps> = {
    showServerGroups: true,
    showInstances : false
  };

  private stateChangeListener: Subscription;
  private baseRef: string;

  constructor(props: ILoadBalancerProps) {
    super(props);
    this.state = {
      active: this.isActive()
    }

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
    const { loadBalancer } = this.props;
    return ReactInjector.$state.includes('**.loadBalancerDetails', {region: loadBalancer.region, accountId: loadBalancer.account, name: loadBalancer.name, vpcId: loadBalancer.vpcId, provider: loadBalancer.cloudProvider});
  }

  private loadDetails(event: React.MouseEvent<HTMLElement>): void {
    event.persist();
    const { $state } = ReactInjector;
    $timeout(() => {
      const { application, loadBalancer } = this.props;
      // anything handled by ui-sref or actual links should be ignored
      if (event.defaultPrevented || (event.nativeEvent && event.nativeEvent.defaultPrevented)) {
        return;
      }
      event.stopPropagation();
      const params = {
        application: application.name,
        region: loadBalancer.region,
        accountId: loadBalancer.account,
        name: loadBalancer.name,
        vpcId: loadBalancer.vpcId,
        provider: loadBalancer.cloudProvider,
      };

      if (isEqual($state.params, params)) {
        // already there
        return;
      }
      // also stolen from uiSref directive
      $state.go('.loadBalancerDetails', params, {relative: this.baseRef, inherit: true});
    });
  }

  private refCallback(element: HTMLElement): void {
    this.baseRef = this.baseRef || last(get<PathNode[]>($(element).parent().inheritedData('$uiView'), '$cfg.path')).state.name;
  }

  public componentWillUnmount(): void {
    this.stateChangeListener.unsubscribe();
  }

  public render(): React.ReactElement<LoadBalancer> {
    const { application, loadBalancer, serverGroups, showInstances, showServerGroups } = this.props;
    const { EntityUiTags } = NgReact;
    const { cloudProviderRegistry } = ReactInjector;
    const config = cloudProviderRegistry.getValue(loadBalancer.provider || loadBalancer.cloudProvider, 'loadBalancer');
    const ClusterContainer = config.ClusterContainer || LoadBalancerClusterContainer;

    return (
      <div
        className="pod-subgroup load-balancer"
        ref={this.refCallback}
      >
        <div
          className={`load-balancer-header clickable clickable-row ${this.state.active ? 'active' : ''}`}
          onClick={this.loadDetails}
        >
          <Sticky topOffset={36}>
            <h6>
              <span className="icon icon-elb"/> {(loadBalancer.region || '').toUpperCase()}
              <EntityUiTags
                component={loadBalancer}
                application={application}
                entityType="loadBalancer"
                pageLocation="pod"
                onUpdate={application.loadBalancers.refresh}
              />
              <span className="text-right">
                <HealthCounts container={loadBalancer.instanceCounts}/>
              </span>
            </h6>
          </Sticky>
        </div>
        <ClusterContainer
          loadBalancer={loadBalancer}
          serverGroups={serverGroups}
          showServerGroups={showServerGroups}
          showInstances={showInstances}
        />
      </div>
    );
  }
}
