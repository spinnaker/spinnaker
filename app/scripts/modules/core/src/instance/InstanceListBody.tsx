import * as React from 'react';
import { Subject } from 'rxjs';
import * as classNames from 'classnames';

import { IServerGroup, IInstance, ILoadBalancerHealth } from 'core/domain';
import { ReactInjector } from 'core/reactShims';
import { timestamp } from 'core/utils/timeFormatters';
import { Tooltip } from 'core/presentation';
import { ClusterState } from 'core/state';

export interface IInstanceListBodyProps {
  serverGroup: IServerGroup;
  instances: IInstance[];
  hasDiscovery: boolean;
  hasLoadBalancers: boolean;
}

export interface IInstanceListBodyState {
  selectedInstanceIds: string[];
  activeInstanceId: string;
  multiselect: boolean;
}

export class InstanceListBody extends React.Component<IInstanceListBodyProps, IInstanceListBodyState> {
  private clusterFilterModel = ClusterState.filterModel.asFilterModel;
  private $uiRouter = ReactInjector.$uiRouter;
  private $state = ReactInjector.$state;
  private destroy$ = new Subject();

  constructor(props: IInstanceListBodyProps) {
    super(props);
    this.state = {
      selectedInstanceIds: this.getSelectedInstanceIds(),
      activeInstanceId: this.$state.params.instanceId,
      multiselect: this.$state.params.multiselect,
    };
  }

  public componentDidMount() {
    ClusterState.multiselectModel.instancesStream.takeUntil(this.destroy$).subscribe(() => {
      this.setState({ selectedInstanceIds: this.getSelectedInstanceIds() });
    });

    this.$uiRouter.globals.params$
      .map(params => params.instanceId + params.multiselect)
      .distinctUntilChanged()
      .takeUntil(this.destroy$)
      .subscribe(() => {
        this.setState({
          activeInstanceId: this.$state.params.instanceId,
          multiselect: this.$state.params.multiselect,
        });
      });
  }

  private getSelectedInstanceIds(): string[] {
    const { instances, serverGroup } = this.props;
    if (this.clusterFilterModel.sortFilter.multiselect) {
      return instances.filter(i => ClusterState.multiselectModel.instanceIsSelected(serverGroup, i.id)).map(i => i.id);
    }
    return [];
  }

  public componentWillUnmount() {
    this.destroy$.next();
  }

  public shouldComponentUpdate(nextProps: IInstanceListBodyProps, nextState: IInstanceListBodyState): boolean {
    if (this.props.serverGroup.stringVal !== nextProps.serverGroup.stringVal) {
      return true;
    }
    if (
      this.props.instances
        .map(i => i.id)
        .sort()
        .join(',') !==
      nextProps.instances
        .map(i => i.id)
        .sort()
        .join(',')
    ) {
      return true;
    }
    return (
      this.state.activeInstanceId !== nextState.activeInstanceId ||
      this.state.selectedInstanceIds.sort().join(',') !== nextState.selectedInstanceIds.sort().join(',') ||
      this.state.multiselect !== nextState.multiselect
    );
  }

  private instanceSorter = (a1: IInstance, b1: IInstance): number => {
    const { sortFilter } = this.clusterFilterModel;
    const filterSplit = sortFilter.instanceSort.split('-'),
      filterType = filterSplit.length === 1 ? filterSplit[0] : filterSplit[1],
      reverse = filterSplit.length === 2,
      a = reverse ? b1 : a1,
      b = reverse ? a1 : b1;

    switch (filterType) {
      case 'id':
        return a.id.localeCompare(b.id);
      case 'launchTime':
        return a.launchTime === b.launchTime ? a.id.localeCompare(b.id) : a.launchTime - b.launchTime;
      case 'availabilityZone':
        return a.availabilityZone === b.availabilityZone
          ? a.launchTime === b.launchTime
            ? a.id.localeCompare(b.id)
            : a.launchTime - b.launchTime
          : a.availabilityZone.localeCompare(b.availabilityZone);
      case 'discoveryState':
        const aHealth = (a.health || []).filter(health => health.type === 'Discovery'),
          bHealth = (b.health || []).filter(health => health.type === 'Discovery');
        if (aHealth.length && !bHealth.length) {
          return -1;
        }
        if (!aHealth.length && bHealth.length) {
          return 1;
        }
        return (!aHealth.length && !bHealth.length) || aHealth[0].state === bHealth[0].state
          ? a.launchTime === b.launchTime
            ? a.id.localeCompare(b.id)
            : a.launchTime - b.launchTime
          : aHealth[0].state.localeCompare(bHealth[0].state);
      case 'loadBalancerSort':
        const aHealth2 = (a.health || []).filter(health => health.type === 'LoadBalancer');
        const bHealth2 = (b.health || []).filter(health => health.type === 'LoadBalancer');

        if (aHealth2.length && !bHealth2.length) {
          return -1;
        }
        if (!aHealth2.length && bHealth2.length) {
          return 1;
        }
        const aHealthStr = aHealth2.map(h => h.loadBalancers.map(l => l.name + ':' + l.state)).join(','),
          bHealthStr = bHealth2.map(h => h.loadBalancers.map(l => l.name + ':' + l.state)).join(',');
        return aHealthStr === bHealthStr
          ? a.launchTime === b.launchTime
            ? a.id.localeCompare(b.id)
            : a.launchTime - b.launchTime
          : aHealthStr.localeCompare(bHealthStr);
      default:
        return -1;
    }
  };

  private renderRow(instance: IInstance): JSX.Element {
    const { hasLoadBalancers, hasDiscovery } = this.props;
    const showProviderHealth = !hasLoadBalancers && !hasDiscovery;

    const healthMetrics = instance.health || [];
    let discoveryStatus = '-';
    let providerStatus = '';
    let loadBalancers: ILoadBalancerHealth[] = [];

    healthMetrics.forEach(health => {
      if (hasLoadBalancers && health.type === 'LoadBalancer') {
        loadBalancers = health.loadBalancers;
      }
      if (hasDiscovery && health.type === 'Discovery') {
        discoveryStatus = health.state;
      }
      if (showProviderHealth) {
        providerStatus = health.state;
      }
    });

    const isActive = this.state.activeInstanceId === instance.id;
    const rowClass = classNames({
      clickable: true,
      active: isActive,
    });

    return (
      <tr key={instance.id} data-instance-id={instance.id} className={rowClass}>
        {this.$state.params.multiselect && (
          <td className="no-hover">
            <input type="checkbox" checked={this.state.selectedInstanceIds.includes(instance.id)} />
          </td>
        )}
        <td>
          <span className={`glyphicon glyphicon-${instance.healthState}-triangle`} />
          {instance.name || instance.id}
        </td>
        <td>{timestamp(instance.launchTime)}</td>
        <td>{instance.availabilityZone}</td>
        {hasDiscovery && <td className="text-center small">{discoveryStatus}</td>}
        {hasLoadBalancers && this.renderLoadBalancerCell(loadBalancers)}
        {showProviderHealth && <td className="text-center small">{providerStatus}</td>}
      </tr>
    );
  }

  private renderLoadBalancerCell(loadBalancerHealths: ILoadBalancerHealth[]): JSX.Element {
    return (
      <td>
        {loadBalancerHealths.length === 0 && <span>-</span>}
        {loadBalancerHealths.map(h => {
          const tooltip = h.state === 'OutOfService' ? h.description.replace(/"/g, '&quot;') : null;
          const icon = h.healthState === 'Up' || h.state === 'InService' ? 'Up' : 'Down';
          return (
            <div key={h.name}>
              {tooltip && (
                <Tooltip value={tooltip} placement="left">
                  <div>
                    <span className={`glyphicon-${icon}-triangle`} />
                    {h.name}
                  </div>
                </Tooltip>
              )}
              {!tooltip && <span className={`glyphicon-${icon}-triangle`}>{h.name}</span>}
            </div>
          );
        })}
      </td>
    );
  }

  private instanceBodyClicked = (event: React.MouseEvent<HTMLElement>): void => {
    const target = event.target as HTMLElement;
    const targetRow = target.closest('tr');
    if (!targetRow) {
      return;
    }
    ClusterState.multiselectModel.toggleInstance(this.props.serverGroup, targetRow.getAttribute('data-instance-id'));
    event.stopPropagation();
  };

  public render() {
    return (
      <tbody onClick={this.instanceBodyClicked}>
        {this.props.instances.sort(this.instanceSorter).map(i => this.renderRow(i))}
      </tbody>
    );
  }
}
