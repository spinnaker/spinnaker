import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';
import { isEqual, flatten, get, last, map, orderBy } from 'lodash';
import { $timeout } from 'ngimport';
import { PathNode } from 'angular-ui-router';
import { Subscription } from 'rxjs';

import { Application } from 'core/application/application.model';
import { ILoadBalancer, IInstance, IServerGroup } from 'core/domain';
import { $state } from 'core/uirouter';
import { clusterFilterService } from 'core/cluster/filter/clusterFilter.service';

import { EntityUiTags } from 'core/entityTag/EntityUiTags';
import { HealthCounts } from 'core/healthCounts/HealthCounts';
import { Instances } from 'core/instance/Instances';
import { LoadBalancerServerGroup } from './LoadBalancerServerGroup';
import { Sticky } from 'core/utils/stickyHeader/Sticky';
import { stateEvents } from 'core/state.events';

interface IProps {
  application: Application,
  loadBalancer: ILoadBalancer;
  serverGroups: IServerGroup[];
  showServerGroups?: boolean;
  showInstances?: boolean;
}

interface IState {
  active: boolean;
  instances: IInstance[];
}

@autoBindMethods
export class LoadBalancer extends React.Component<IProps, IState> {
  public static defaultProps: Partial<IProps> = {
    showServerGroups: true,
    showInstances : false
  };

  private stateChangeListener: Subscription;
  private clusterChangeListener: Subscription;
  private baseRef: string;

  constructor(props: IProps) {
    super(props);
    this.state = this.getState(props);

    this.stateChangeListener = stateEvents.stateChangeSuccess.subscribe(
      () => {
        const active = this.isActive()
        if (this.state.active !== active) {
          this.setState({active});
        }
      }
    );

    this.clusterChangeListener = clusterFilterService.groupsUpdatedStream.subscribe(() => this.setState(this.getState(this.props)));
  }

  private isActive(): boolean {
    const { loadBalancer } = this.props;
    return $state.includes('**.loadBalancerDetails', {region: loadBalancer.region, accountId: loadBalancer.account, name: loadBalancer.name, vpcId: loadBalancer.vpcId, provider: loadBalancer.cloudProvider});
  }

  private getState(props: IProps): IState {
    const { loadBalancer } = props;

    return {
      active: this.isActive(),
      instances: loadBalancer.instances.concat(flatten(map<IInstance, IInstance>(loadBalancer.serverGroups, 'detachedInstances'))),
    };
  }

  private loadDetails(event: React.MouseEvent<HTMLElement>): void {
    event.persist();
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

  public componentWillReceiveProps(nextProps: IProps): void {
    this.setState(this.getState(nextProps));
  }

  public componentWillUnmount(): void {
    this.stateChangeListener.unsubscribe();
    this.clusterChangeListener.unsubscribe();
  }

  public render(): React.ReactElement<LoadBalancer> {
    const { application, loadBalancer, serverGroups, showInstances, showServerGroups } = this.props;

    const ServerGroups = orderBy(serverGroups, ['isDisabled', 'name'], ['asc', 'desc']).map((serverGroup) => (
      <LoadBalancerServerGroup
        key={serverGroup.name}
        loadBalancer={loadBalancer}
        serverGroup={serverGroup}
        showInstances={showInstances}
      />
    ));

    return (
      <div
        className={`pod-subgroup clickable clickable-row ${this.state.active ? 'active' : ''}`}
        onClick={this.loadDetails}
        ref={this.refCallback}
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
        <div className="cluster-container">
          {showServerGroups && <div>{ServerGroups}</div>}
          {!showServerGroups && showInstances && <div className="instance-list"><Instances instances={this.state.instances}/></div>}
        </div>
      </div>
    );
  }
}
