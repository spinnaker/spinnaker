// This is all mercilessly copied from LoadBalancersTag.tsx. This should be clenaed up at some point
// Probably when we convert clusters view to React.

import * as React from 'react';
import * as ReactGA from 'react-ga';
import autoBindMethods from 'class-autobind-decorator';
import { sortBy } from 'lodash';

import { HealthCounts, ILoadBalancer, ILoadBalancersTagProps, LoadBalancerDataUtils, ReactInjector, Tooltip } from '@spinnaker/core';

import { AmazonLoadBalancerDataUtils } from 'amazon/loadBalancer/amazonLoadBalancerDataUtils';
import { IAmazonServerGroup, ITargetGroup } from 'amazon/domain';

export interface IAmazonLoadBalancersTagState {
  loadBalancers: ILoadBalancer[];
  targetGroups: ITargetGroup[];
  showPopover: boolean;
};

interface ILoadBalancerListItemProps {
  loadBalancer: ILoadBalancer | ITargetGroup;
  onItemClick: (loadBalancer: ILoadBalancer | ITargetGroup) => void;
}

@autoBindMethods
class LoadBalancerListItem extends React.Component<ILoadBalancerListItemProps, void> {
  private onClick(e: React.MouseEvent<HTMLElement>): void {
    this.props.onItemClick(this.props.loadBalancer);
    e.nativeEvent.preventDefault(); // yay angular JQueryEvent still listening to the click event...
  }

  public render(): React.ReactElement<LoadBalancerListItem> {
    return (
      <a onClick={this.onClick}>
        <span className="name">{this.props.loadBalancer.name}</span>
        <HealthCounts container={this.props.loadBalancer.instanceCounts}/>
      </a>
    )
  }
}

@autoBindMethods
class LoadBalancerButton extends React.Component<ILoadBalancerListItemProps, void> {
  private onClick(e: React.MouseEvent<HTMLElement>): void {
    this.props.onItemClick(this.props.loadBalancer);
    e.nativeEvent.preventDefault(); // yay angular JQueryEvent still listening to the click event...
  }

  public render(): React.ReactElement<LoadBalancerButton> {
    return (
      <Tooltip value={`Load Balancer: ${this.props.loadBalancer.name}`}>
        <button className="btn btn-link no-padding" onClick={this.onClick}>
          <span className="badge badge-counter">
            <span className="icon">
              <span className="icon-elb"/>
            </span>
          </span>
        </button>
      </Tooltip>
    )
  }
}

@autoBindMethods
export class AmazonLoadBalancersTag extends React.Component<ILoadBalancersTagProps, IAmazonLoadBalancersTagState> {
  private loadBalancersRefreshUnsubscribe: () => void;

  constructor(props: ILoadBalancersTagProps) {
    super(props);
    this.state = {
      loadBalancers: [],
      targetGroups: [],
      showPopover: false
    };

    LoadBalancerDataUtils.populateLoadBalancers(props.application, props.serverGroup).then((loadBalancers) => this.setState({loadBalancers}))
    AmazonLoadBalancerDataUtils.populateTargetGroups(props.application, props.serverGroup as IAmazonServerGroup).then((targetGroups: ITargetGroup[]) => this.setState({targetGroups}))

    this.loadBalancersRefreshUnsubscribe = props.application.getDataSource('loadBalancers').onRefresh(null, () => { this.forceUpdate(); });
  }

  private showLoadBalancerDetails(loadBalancer: ILoadBalancer): void {
    const { $state } = ReactInjector;
    const serverGroup = this.props.serverGroup;
    ReactGA.event({category: 'Cluster Pod', action: `Load Load Balancer Details (multiple menu)`});
    const nextState = $state.current.name.endsWith('.clusters') ? '.loadBalancerDetails' : '^.loadBalancerDetails';
    $state.go(nextState, {region: serverGroup.region, accountId: serverGroup.account, name: loadBalancer.name, provider: serverGroup.type});
  }

  private showTargetGroupDetails(targetGroup: ITargetGroup): void {
    const { $state } = ReactInjector;
    const serverGroup = this.props.serverGroup;
    ReactGA.event({category: 'Cluster Pod', action: `Load Target Group Details (multiple menu)`});
    const nextState = $state.current.name.endsWith('.clusters') ? '.targetGroupDetails' : '^.targetGroupDetails';
    $state.go(nextState, {region: serverGroup.region, accountId: serverGroup.account, name: targetGroup.name, provider: serverGroup.type, loadBalancerName: targetGroup.loadBalancerNames[0]});
  }

  private toggleShowPopover(e: React.MouseEvent<HTMLElement>): void {
    ReactGA.event({category: 'Cluster Pod', action: `Toggle Load Balancers Menu (${this.state.showPopover})`});
    this.setState({showPopover: !this.state.showPopover});
    e.preventDefault();
    e.stopPropagation();
  }

  public componentWillUnmount(): void {
    if (this.loadBalancersRefreshUnsubscribe) {
      this.loadBalancersRefreshUnsubscribe();
    }
  }

  public render(): React.ReactElement<AmazonLoadBalancersTag> {
    const { loadBalancers, showPopover, targetGroups } = this.state;

    const targetGroupCount = targetGroups && targetGroups.length || 0,
          loadBalancerCount = loadBalancers && loadBalancers.length || 0,
          totalCount = targetGroupCount + loadBalancerCount;

    if (totalCount) {
      const showValue = targetGroupCount ? loadBalancerCount ? 'target groups and load balancers' : 'target groups' : 'load balancers';

      return (
        <span className={totalCount > 1 ? 'overflowing' : ''}>
          { totalCount > 1 && (
            <Tooltip value={`${showPopover ? 'Hide' : 'Show'} all ${totalCount} ${showValue}`}>
              <button
                className="btn btn-link btn-multiple-load-balancers clearfix no-padding"
                onClick={this.toggleShowPopover}
              >
                <span className="badge badge-counter">
                  <span className="icon"><span className="icon-elb"/></span> {totalCount}
                </span>
              </button>
            </Tooltip>
          )}
          { showPopover && (
            <div className="menu-load-balancers">
              {loadBalancerCount > 0 && <div className="menu-load-balancers-header">Load Balancers</div>}
              {sortBy(loadBalancers, 'name').map((loadBalancer) => <LoadBalancerListItem key={loadBalancer.name} loadBalancer={loadBalancer} onItemClick={this.showLoadBalancerDetails}/>)}
              {targetGroupCount > 0 && <div className="menu-load-balancers-header">Target Groups</div>}
              {sortBy(targetGroups, 'name').map((targetGroup) => <LoadBalancerListItem key={targetGroup.name} loadBalancer={targetGroup} onItemClick={this.showTargetGroupDetails}/>)}
            </div>
          )}
          { (loadBalancers.length === 1 && targetGroups.length === 0) && (
            <span className="btn-load-balancer">
              <LoadBalancerButton key={loadBalancers[0].name} loadBalancer={loadBalancers[0]} onItemClick={this.showLoadBalancerDetails}/>
            </span>
          )}
          { (targetGroups.length === 1 && loadBalancers.length === 0) && (
            <span className="btn-load-balancer">
              <LoadBalancerButton key={targetGroups[0].name} loadBalancer={targetGroups[0]} onItemClick={this.showTargetGroupDetails}/>
            </span>
          )}
        </span>
      )
    } else {
      return null;
    }
  }
}
