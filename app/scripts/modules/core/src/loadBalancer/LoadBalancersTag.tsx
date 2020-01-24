import React from 'react';
import ReactGA from 'react-ga';
import { sortBy } from 'lodash';

import { ILoadBalancer } from 'core/domain';
import { ILoadBalancersTagProps } from './LoadBalancersTagWrapper';
import { HealthCounts } from 'core/healthCounts/HealthCounts';
import { LoadBalancerDataUtils } from './loadBalancerDataUtils';
import { Tooltip } from 'core/presentation/Tooltip';
import { ReactInjector } from 'core/reactShims';
import { HoverablePopover } from 'core/presentation';
import { Spinner } from 'core/widgets';

interface ILoadBalancerListItemProps {
  loadBalancer: ILoadBalancer;
  onItemClick: (loadBalancer: ILoadBalancer) => void;
}

class LoadBalancerListItem extends React.Component<ILoadBalancerListItemProps> {
  private onClick = (e: React.MouseEvent<HTMLElement>): void => {
    this.props.onItemClick(this.props.loadBalancer);
    e.nativeEvent.preventDefault(); // yay angular JQueryEvent still listening to the click event...
  };

  public render(): React.ReactElement<LoadBalancerListItem> {
    return (
      <a onClick={this.onClick}>
        <span className="name">{this.props.loadBalancer.name}</span>
        <HealthCounts container={this.props.loadBalancer.instanceCounts} />
      </a>
    );
  }
}

class LoadBalancerButton extends React.Component<ILoadBalancerListItemProps> {
  private onClick = (e: React.MouseEvent<HTMLElement>): void => {
    this.props.onItemClick(this.props.loadBalancer);
    e.nativeEvent.preventDefault(); // yay angular JQueryEvent still listening to the click event...
  };

  public render(): React.ReactElement<LoadBalancerButton> {
    return (
      <Tooltip value={`Load Balancer: ${this.props.loadBalancer.name}`}>
        <button className="btn btn-link no-padding" onClick={this.onClick}>
          <span className="badge badge-counter">
            <span className="icon">
              <i className="fa icon-sitemap" />
            </span>
          </span>
        </button>
      </Tooltip>
    );
  }
}

export interface ILoadBalancersTagState {
  loadBalancers: ILoadBalancer[];
  isLoading: boolean;
}

export class LoadBalancersTag extends React.Component<ILoadBalancersTagProps, ILoadBalancersTagState> {
  private loadBalancersRefreshUnsubscribe: () => void;

  constructor(props: ILoadBalancersTagProps) {
    super(props);
    this.state = {
      loadBalancers: [],
      isLoading: true,
    };
  }
  private showLoadBalancerDetails = (loadBalancer: ILoadBalancer): void => {
    const { $state } = ReactInjector;
    const serverGroup = this.props.serverGroup;
    ReactGA.event({ category: 'Cluster Pod', action: `Load Load Balancer Details (multiple menu)` });
    const nextState = $state.current.name.endsWith('.clusters') ? '.loadBalancerDetails' : '^.loadBalancerDetails';
    $state.go(nextState, {
      region: serverGroup.region,
      accountId: serverGroup.account,
      name: loadBalancer.name,
      provider: serverGroup.type,
    });
  };

  private handleShowPopover = () => {
    ReactGA.event({ category: 'Cluster Pod', action: `Show Load Balancers Menu` });
  };

  private handleClick = (e: React.MouseEvent<HTMLElement>): void => {
    e.preventDefault();
    e.stopPropagation();
  };

  public componentDidMount(): void {
    LoadBalancerDataUtils.populateLoadBalancers(this.props.application, this.props.serverGroup).then(loadBalancers =>
      this.setState({ loadBalancers, isLoading: false }),
    );

    this.loadBalancersRefreshUnsubscribe = this.props.application.getDataSource('loadBalancers').onRefresh(null, () => {
      this.forceUpdate();
    });
  }

  public componentWillUnmount(): void {
    this.loadBalancersRefreshUnsubscribe();
  }

  public render(): React.ReactElement<LoadBalancersTag> {
    const { loadBalancers, isLoading } = this.state;

    const totalCount = loadBalancers.length;

    if (isLoading) {
      return <Spinner size="nano" />;
    } else if (!totalCount) {
      return null;
    }

    const className = `load-balancers-tag ${totalCount > 1 ? 'overflowing' : ''}`;

    const popover = (
      <div className="menu-load-balancers">
        <div className="menu-load-balancers-header"> Load Balancers </div>
        {sortBy(loadBalancers, 'name').map(loadBalancer => (
          <LoadBalancerListItem
            key={loadBalancer.name}
            loadBalancer={loadBalancer}
            onItemClick={this.showLoadBalancerDetails}
          />
        ))}
      </div>
    );

    return (
      <span className={className}>
        {totalCount > 1 && (
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
            <button onClick={this.handleClick} className="btn btn-link btn-multiple-load-balancers clearfix no-padding">
              <span className="badge badge-counter">
                <span className="icon">
                  <i className="fa icon-sitemap" />
                </span>{' '}
                {totalCount}
              </span>
            </button>
          </HoverablePopover>
        )}

        {totalCount === 1 && (
          <span className="btn-load-balancer">
            {sortBy(loadBalancers, 'name').map(loadBalancer => (
              <LoadBalancerButton
                key={loadBalancer.name}
                loadBalancer={loadBalancer}
                onItemClick={this.showLoadBalancerDetails}
              />
            ))}
          </span>
        )}
      </span>
    );
  }
}
