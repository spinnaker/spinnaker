import * as React from 'react';
import { flatten, map } from 'lodash';
import { Subscription } from 'rxjs';

import { IInstance, IServerGroup } from 'core/domain';
import { ReactInjector } from 'core/reactShims';
import { Instances } from 'core/instance/Instances';

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

    const { clusterFilterService } = ReactInjector;
    this.clusterChangeListener = clusterFilterService.groupsUpdatedStream.subscribe(() =>
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
