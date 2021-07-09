import classNames from 'classnames';
import { isEqual } from 'lodash';
import React from 'react';
import { Subject } from 'rxjs';
import { distinctUntilChanged, map, takeUntil } from 'rxjs/operators';

import { IInstance, ILoadBalancerHealth, IServerGroup } from '../domain';
import { Tooltip } from '../presentation';
import { ReactInjector } from '../reactShims';
import { ClusterState } from '../state';
import { timestamp } from '../utils/timeFormatters';

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
  instanceSort?: string;
}

export class InstanceListBody extends React.Component<IInstanceListBodyProps, IInstanceListBodyState> {
  private $uiRouter = ReactInjector.$uiRouter;
  private $state = ReactInjector.$state;
  private destroy$ = new Subject();

  constructor(props: IInstanceListBodyProps) {
    super(props);
    this.state = {
      selectedInstanceIds: this.getSelectedInstanceIds(),
      activeInstanceId: this.$state.params.instanceId,
      multiselect: this.$state.params.multiselect,
      instanceSort: this.$state.params.instanceSort,
    };
  }

  public componentDidMount() {
    ClusterState.multiselectModel.instancesStream.pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.setState({ selectedInstanceIds: this.getSelectedInstanceIds() });
    });

    this.$uiRouter.globals.params$
      .pipe(
        map((params) => [params.instanceId, params.multiselect, params.instanceSort]),
        distinctUntilChanged(isEqual),
        takeUntil(this.destroy$),
      )
      .subscribe(() => {
        const { params } = this.$state;
        this.setState({
          activeInstanceId: params.instanceId,
          multiselect: params.multiselect,
          instanceSort: params.instanceSort,
        });
      });
  }

  private getSelectedInstanceIds(): string[] {
    const { instances, serverGroup } = this.props;
    if (this.$state.params.multiselect) {
      return instances
        .filter((i) => ClusterState.multiselectModel.instanceIsSelected(serverGroup, i.id))
        .map((i) => i.id);
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
        .map((i) => i.id)
        .sort()
        .join(',') !==
      nextProps.instances
        .map((i) => i.id)
        .sort()
        .join(',')
    ) {
      return true;
    }
    const { state } = this;
    return (
      state.activeInstanceId !== nextState.activeInstanceId ||
      state.selectedInstanceIds.sort().join(',') !== nextState.selectedInstanceIds.sort().join(',') ||
      state.multiselect !== nextState.multiselect ||
      state.instanceSort !== nextState.instanceSort
    );
  }

  private instanceSorter = (a1: IInstance, b1: IInstance): number => {
    const { instanceSort = 'launchTime' } = this.state;
    const filterSplit = instanceSort.split('-');
    const filterType = filterSplit.length === 1 ? filterSplit[0] : filterSplit[1];
    const reverse = filterSplit.length === 2;
    const a = reverse ? b1 : a1;
    const b = reverse ? a1 : b1;

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
      case 'discoveryState': {
        const aHealth = (a.health || []).filter((health) => health.type === 'Discovery');
        const bHealth = (b.health || []).filter((health) => health.type === 'Discovery');
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
      }
      case 'loadBalancerSort': {
        const aHealth2 = (a.health || []).filter((health) => health.type === 'LoadBalancer');
        const bHealth2 = (b.health || []).filter((health) => health.type === 'LoadBalancer');

        if (aHealth2.length && !bHealth2.length) {
          return -1;
        }
        if (!aHealth2.length && bHealth2.length) {
          return 1;
        }
        const aHealthStr = aHealth2.map((h) => h.loadBalancers.map((l) => l.name + ':' + l.state)).join(',');
        const bHealthStr = bHealth2.map((h) => h.loadBalancers.map((l) => l.name + ':' + l.state)).join(',');
        return aHealthStr === bHealthStr
          ? a.launchTime === b.launchTime
            ? a.id.localeCompare(b.id)
            : a.launchTime - b.launchTime
          : aHealthStr.localeCompare(bHealthStr);
      }
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

    healthMetrics.forEach((health) => {
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
        {loadBalancerHealths.map((h) => {
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
        {this.props.instances.sort(this.instanceSorter).map((i) => this.renderRow(i))}
      </tbody>
    );
  }
}
