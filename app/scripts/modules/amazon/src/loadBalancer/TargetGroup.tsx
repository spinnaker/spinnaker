import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';
import { $timeout } from 'ngimport';
import { get, isEqual, last, orderBy } from 'lodash';
import { PathNode } from '@uirouter/angularjs';
import { Subscription } from 'rxjs';

import { HealthCounts, LoadBalancerInstances, LoadBalancerServerGroup, ReactInjector } from '@spinnaker/core';

import { IAmazonApplicationLoadBalancer, ITargetGroup } from 'amazon/domain/IAmazonLoadBalancer';

import './targetGroup.less';

export interface ITargetGroupProps {
  loadBalancer: IAmazonApplicationLoadBalancer;
  targetGroup: ITargetGroup;
  showServerGroups: boolean;
  showInstances: boolean;
}

export interface ITargetGroupState {
  active: boolean;
}

@autoBindMethods
export class TargetGroup extends React.Component<ITargetGroupProps, ITargetGroupState> {
  private stateChangeListener: Subscription;
  private baseRef: string;

  constructor(props: ITargetGroupProps) {
    super(props);
    this.state = { active: this.isActive() };

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
    const { targetGroup } = this.props;
    return ReactInjector.$state.includes('**.targetGroupDetails', {region: targetGroup.region, accountId: targetGroup.account, name: targetGroup.name, vpcId: targetGroup.vpcId, provider: targetGroup.cloudProvider});
  }

  private loadDetails(event: React.MouseEvent<HTMLElement>): void {
    event.persist();
    const { $state } = ReactInjector;
    $timeout(() => {
      const { loadBalancer, targetGroup } = this.props;
      // anything handled by ui-sref or actual links should be ignored
      if (event.defaultPrevented || (event.nativeEvent && event.nativeEvent.defaultPrevented)) {
        return;
      }
      event.stopPropagation();
      const params = {
        loadBalancerName: loadBalancer.name,
        region: targetGroup.region,
        accountId: targetGroup.account,
        name: targetGroup.name,
        vpcId: targetGroup.vpcId,
        provider: targetGroup.cloudProvider,
      };

      if (isEqual($state.params, params)) {
        // already there
        return;
      }
      // also stolen from uiSref directive
      $state.go('.targetGroupDetails', params, {relative: this.baseRef, inherit: true});
    });
  }

  public componentWillUnmount(): void {
    this.stateChangeListener.unsubscribe();
  }

  private refCallback(element: HTMLElement): void {
    this.baseRef = this.baseRef || last(get<PathNode[]>($(element).parent().inheritedData('$uiView'), '$cfg.path')).state.name;
  }

  public render(): React.ReactElement<TargetGroup> {
    const { targetGroup, showInstances, showServerGroups } = this.props;

    const ServerGroups = orderBy(targetGroup.serverGroups, ['isDisabled', 'name'], ['asc', 'desc']).map((serverGroup) => (
      <LoadBalancerServerGroup
        key={serverGroup.name}
        account={targetGroup.account}
        region={targetGroup.region}
        cloudProvider={targetGroup.cloudProvider}
        serverGroup={serverGroup}
        showInstances={showInstances}
      />
    ));

    return (
      <div className="target-group-container container-fluid no-padding" ref={this.refCallback}>
        <div className={`clickable clickable-row row no-margin-top target-group-header${this.state.active ? ' active' : ''}`} onClick={this.loadDetails}>
          <div className="col-md-8 target-group-title">
              {targetGroup.name}
          </div>
          <div className="col-md-4 text-right">
            <HealthCounts container={targetGroup.instanceCounts}/>
          </div>
        </div>
        {showServerGroups && ServerGroups}
        {!showServerGroups && showInstances && <LoadBalancerInstances serverGroups={targetGroup.serverGroups} instances={targetGroup.instances} />}
      </div>
    );
  }
}
