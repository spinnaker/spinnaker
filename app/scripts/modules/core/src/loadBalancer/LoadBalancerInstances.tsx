import { flatten, map } from 'lodash';
import React from 'react';
import { Subscription } from 'rxjs';

import { IInstance, IServerGroup } from '../domain';
import { Instances } from '../instance/Instances';
import { ClusterState } from '../state';

export interface ILoadBalancerInstancesProps {
  serverGroups: IServerGroup[];
  instances: IInstance[];
}

export interface ILoadBalancerInstancesState {
  instances: IInstance[];
}

export class LoadBalancerInstances extends React.Component<ILoadBalancerInstancesProps, ILoadBalancerInstancesState> {
  private clusterChangeListener: Subscription;

  constructor(props: ILoadBalancerInstancesProps) {
    super(props);
    this.state = this.getState(props);

    this.clusterChangeListener = ClusterState.filterService.groupsUpdatedStream.subscribe(() =>
      this.setState(this.getState(this.props)),
    );
  }

  private getState(props: ILoadBalancerInstancesProps): ILoadBalancerInstancesState {
    return {
      instances: props.instances.concat(flatten(map<IServerGroup, IInstance>(props.serverGroups, 'detachedInstances'))),
    };
  }

  public componentWillUnmount(): void {
    this.clusterChangeListener.unsubscribe();
  }

  public render(): React.ReactElement<LoadBalancerInstances> {
    return (
      <div className="instance-list">
        <Instances instances={this.state.instances} />
      </div>
    );
  }
}
