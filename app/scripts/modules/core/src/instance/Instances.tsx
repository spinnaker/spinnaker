import { Transition, UIRouter } from '@uirouter/core';
import { UIViewAddress, useParentView, useRouter } from '@uirouter/react';
import React from 'react';
import { Subscription } from 'rxjs';

import { Instance } from './Instance';
import { IInstance } from '../domain';

export interface IInstancesProps {
  instances: IInstance[];
  highlight?: string;
}

export interface IInstancesState {
  detailsInstanceId: string;
}

export const Instances = (props: IInstancesProps) => {
  const router = useRouter();
  const uiview = useParentView();
  return <InstancesInternal {...props} router={router} uiview={uiview} />;
};

export interface IInstancesInternalProps extends IInstancesProps {
  router: UIRouter;
  uiview: UIViewAddress;
}

class InstancesInternal extends React.Component<IInstancesInternalProps, IInstancesState> {
  private subscription: Subscription;

  constructor(props: IInstancesInternalProps) {
    super(props);
    this.state = {
      detailsInstanceId: null,
    };
  }

  public componentDidMount() {
    this.subscription = this.props.router.globals.success$.subscribe(this.onStateChange);
  }

  public componentWillUnmount() {
    this.subscription.unsubscribe();
  }

  private onStateChange = (transition: Transition) => {
    const isShowingDetails = !!/\.instanceDetails/.exec(transition.to().name);
    const detailsInstanceId = isShowingDetails ? transition.params().instanceId : null;
    this.setState({ detailsInstanceId });
  };

  public shouldComponentUpdate(nextProps: IInstancesInternalProps, nextState: IInstancesState) {
    const propsKeys: Array<keyof IInstancesInternalProps> = ['instances', 'highlight'];
    if (propsKeys.some((key) => this.props[key] !== nextProps[key])) {
      return true;
    }

    if (this.state.detailsInstanceId !== nextState.detailsInstanceId) {
      const ids = nextProps.instances.map((x) => x.id);
      if (ids.includes(this.state.detailsInstanceId) || ids.includes(nextState.detailsInstanceId)) {
        return true;
      }
    }

    return false;
  }

  private handleInstanceClicked = (instance: IInstance) => {
    const { router, uiview } = this.props;
    const params = { instanceId: instance.id, provider: instance.cloudProvider || instance.provider };
    const options = { relative: uiview.context };

    router.stateService.go('.instanceDetails', params, options);
  };

  private partitionInstances(): IInstance[][] {
    const partitions: IInstance[][] = [];
    const instances = (this.props.instances || []).sort(
      (a, b) => a.launchTime - b.launchTime || a.id.localeCompare(b.id),
    );
    if (!instances.length) {
      return partitions;
    }
    let currentPartition: IInstance[] = [];
    let currentState = instances[0].healthState;
    instances.forEach((i) => {
      if (i.healthState !== currentState) {
        partitions.push(currentPartition);
        currentPartition = [];
      }
      currentPartition.push(i);
      currentState = i.healthState;
    });
    partitions.push(currentPartition);
    return partitions;
  }

  public render() {
    const partitions = this.partitionInstances();
    return (
      <div className="instances">
        {partitions.map((p, i) => (
          <span key={i} className={`instance-group instance-group-${p[0].healthState}`}>
            {p.map((instance) => (
              <Instance
                key={instance.id}
                instance={instance}
                active={this.state.detailsInstanceId === instance.id}
                highlight={this.props.highlight}
                onInstanceClicked={this.handleInstanceClicked}
              />
            ))}
          </span>
        ))}
      </div>
    );
  }
}
