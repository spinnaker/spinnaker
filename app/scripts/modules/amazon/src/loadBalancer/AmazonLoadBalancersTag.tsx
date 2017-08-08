// This is all mercilessly copied from LoadBalancersTag.tsx. This should be clenaed up at some point
// Probably when we convert clusters view to React.

import * as React from 'react';
import * as ReactGA from 'react-ga';
import autoBindMethods from 'class-autobind-decorator';
import { sortBy } from 'lodash';

import {
  HealthCounts, ILoadBalancer, ILoadBalancersTagProps, LoadBalancerDataUtils, ReactInjector, Tooltip, HoverablePopover
} from '@spinnaker/core';

import { AmazonLoadBalancerDataUtils } from 'amazon/loadBalancer/amazonLoadBalancerDataUtils';
import { IAmazonServerGroup, ITargetGroup } from 'amazon/domain';

interface ILoadBalancerListItemProps {
  loadBalancer: ILoadBalancer | ITargetGroup;
  onItemClick: (loadBalancer: ILoadBalancer | ITargetGroup) => void;
}

interface ILoadBalancerSingleItemProps extends ILoadBalancerListItemProps {
  label: string;
}

@autoBindMethods
class LoadBalancerListItem extends React.Component<ILoadBalancerListItemProps> {
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
class LoadBalancerButton extends React.Component<ILoadBalancerSingleItemProps> {
  private onClick(e: React.MouseEvent<HTMLElement>): void {
    this.props.onItemClick(this.props.loadBalancer);
    e.nativeEvent.preventDefault(); // yay angular JQueryEvent still listening to the click event...
  }

  public render(): React.ReactElement<LoadBalancerButton> {
    return (
      <Tooltip value={`${this.props.label || 'Load Balancer'}: ${this.props.loadBalancer.name}`}>
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

export interface IAmazonLoadBalancersTagState {
  loadBalancers: ILoadBalancer[];
  targetGroups: ITargetGroup[];
}

@autoBindMethods
export class AmazonLoadBalancersTag extends React.Component<ILoadBalancersTagProps, IAmazonLoadBalancersTagState> {
  private loadBalancersRefreshUnsubscribe: () => void;

  constructor(props: ILoadBalancersTagProps) {
    super(props);
    this.state = {
      loadBalancers: [],
      targetGroups: [],
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

  private handleShowPopover() {
    ReactGA.event({category: 'Cluster Pod', action: `Show Load Balancers Menu`});
  }

  private handleClick(e: React.MouseEvent<HTMLElement>): void {
    e.preventDefault();
    e.stopPropagation();
  }

  public componentWillUnmount(): void {
    this.loadBalancersRefreshUnsubscribe();
  }

  public render(): React.ReactElement<AmazonLoadBalancersTag> {
    const { loadBalancers, targetGroups } = this.state;

    const targetGroupCount = targetGroups && targetGroups.length || 0,
          loadBalancerCount = loadBalancers && loadBalancers.length || 0,
          totalCount = targetGroupCount + loadBalancerCount;

    if (!totalCount) {
      return null;
    }

    const className = `load-balancers-tag ${totalCount > 1 ? 'overflowing' : ''}`;
    const popover = (
      <div className="menu-load-balancers">
        {loadBalancerCount > 0 && <div className="menu-load-balancers-header">Load Balancers</div>}
        {sortBy(loadBalancers, 'name').map((loadBalancer) => (
          <LoadBalancerListItem key={loadBalancer.name} loadBalancer={loadBalancer} onItemClick={this.showLoadBalancerDetails}/>
        ))}

        {targetGroupCount > 0 && <div className="menu-load-balancers-header">Target Groups</div>}
        {sortBy(targetGroups, 'name').map((targetGroup) => (
          <LoadBalancerListItem key={targetGroup.name} loadBalancer={targetGroup} onItemClick={this.showTargetGroupDetails}/>
        ))}
      </div>
    );

    return (
      <span className={className}>
        { totalCount > 1 && (
          <HoverablePopover
            delayShow={100}
            delayHide={150}
            onShow={this.handleShowPopover}
            placement="bottom"
            template={popover}
            hOffsetPercent="80%"
            container={this.props.container}
            className="no-padding menu-load-balancers"
          >
            <button onClick={this.handleClick} className="btn btn-link btn-multiple-load-balancers clearfix no-padding" >
              <span className="badge badge-counter">
                <span className="icon"><span className="icon-elb"/></span> {totalCount}
              </span>
            </button>
          </HoverablePopover>
        )}

        { (loadBalancers.length === 1 && targetGroups.length === 0) && (
          <span className="btn-load-balancer">
            <LoadBalancerButton key={loadBalancers[0].name} label="Load Balancer" loadBalancer={loadBalancers[0]} onItemClick={this.showLoadBalancerDetails}/>
          </span>
        )}

        { (targetGroups.length === 1 && loadBalancers.length === 0) && (
          <span className="btn-load-balancer">
            <LoadBalancerButton key={targetGroups[0].name} label="Target Group" loadBalancer={targetGroups[0]} onItemClick={this.showTargetGroupDetails}/>
          </span>
        )}
      </span>
    )
  }
}
