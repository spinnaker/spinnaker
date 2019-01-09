import * as React from 'react';
import * as ReactGA from 'react-ga';
import { has } from 'lodash';
import * as classNames from 'classnames';
import { Subscription } from 'rxjs';

import { ReactInjector } from 'core/reactShims';
import { Application } from 'core/application';
import { IInstance, IServerGroup } from 'core/domain';
import { InstanceList } from 'core/instance/InstanceList';
import { Instances } from 'core/instance/Instances';
import { ScrollToService } from 'core/utils';
import { ISortFilter } from 'core/filterModel';
import { ClusterState } from 'core/state';
import { SETTINGS } from 'core/config';

import { ServerGroupHeader } from './ServerGroupHeader';

export interface IJenkinsViewModel {
  number: number;
  href?: string;
}

export interface IDockerViewModel {
  digest: string;
  image: string;
  tag: string;
  href?: string;
}

export interface IServerGroupProps {
  cluster: string;
  serverGroup: IServerGroup;
  application: Application;
  sortFilter: ISortFilter;
  hasLoadBalancers: boolean;
  hasDiscovery: boolean;
  hasManager?: boolean;
}

export interface IServerGroupState {
  jenkins: IJenkinsViewModel;
  docker: IDockerViewModel;
  instances: IInstance[];
  images?: string[];

  isSelected: boolean; // single select mode
  isMultiSelected: boolean; // multiselect mode
}

export class ServerGroup extends React.Component<IServerGroupProps, IServerGroupState> {
  private stateChangeSubscription: Subscription;
  private serverGroupsSubscription: Subscription;

  constructor(props: IServerGroupProps) {
    super(props);
    this.state = this.getState(props);
  }

  private getState(props: IServerGroupProps): IServerGroupState {
    const { serverGroup } = props;
    const instances = serverGroup.instances.filter(i => ClusterState.filterService.shouldShowInstance(i));
    const isSelected = this.isSelected(serverGroup);
    const isMultiSelected = this.isMultiSelected(props.sortFilter.multiselect, serverGroup);
    const jenkinsConfig = serverGroup.buildInfo && serverGroup.buildInfo.jenkins;
    const dockerConfig = serverGroup.buildInfo && serverGroup.buildInfo.docker;

    let jenkins: IJenkinsViewModel = null;
    let images: string[] = null;
    let docker: IDockerViewModel = null;

    if (jenkinsConfig && (jenkinsConfig.host || jenkinsConfig.fullUrl || serverGroup.buildInfo.buildInfoUrl)) {
      const fromHost =
        jenkinsConfig.host && [jenkinsConfig.host + 'job', jenkinsConfig.name, jenkinsConfig.number, ''].join('/');
      const fromFullUrl = jenkinsConfig.fullUrl;
      const fromBuildInfo = serverGroup.buildInfo.buildInfoUrl;

      jenkins = {
        number: jenkinsConfig.number,
        href: fromBuildInfo || fromFullUrl || fromHost,
      };
    } else if (SETTINGS.dockerInsights.enabled && dockerConfig) {
      docker = {
        digest: dockerConfig.digest,
        tag: dockerConfig.tag,
        image: dockerConfig.image,
        href:
          SETTINGS.dockerInsights.url +
          'images/' +
          encodeURIComponent(dockerConfig.image) +
          '/' +
          (dockerConfig.tag || dockerConfig.digest),
      };
    } else if (has(serverGroup, 'buildInfo.images')) {
      images = serverGroup.buildInfo.images;
    }

    return {
      jenkins,
      instances,
      images,
      docker,
      isSelected,
      isMultiSelected,
    };
  }

  private isSelected(serverGroup: IServerGroup) {
    const params = {
      region: serverGroup.region,
      accountId: serverGroup.account,
      serverGroup: serverGroup.name,
      provider: serverGroup.type,
    };

    return ReactInjector.$state.includes('**.serverGroup', params);
  }

  private isMultiSelected(multiselect: boolean, serverGroup: IServerGroup) {
    return multiselect && ClusterState.multiselectModel.serverGroupIsSelected(serverGroup);
  }

  private onServerGroupsChanged = () => {
    const isMultiSelected = this.isMultiSelected(this.props.sortFilter.multiselect, this.props.serverGroup);
    this.setState({ isMultiSelected });
    // Enables the (angular) details pane to detect the changes
    ReactInjector.$rootScope.$applyAsync(() => false);
  };

  private onStateChanged = () => {
    this.setState({ isSelected: this.isSelected(this.props.serverGroup) });
  };

  public componentDidMount(): void {
    const { serverGroupsStream, instancesStream } = ClusterState.multiselectModel;

    this.serverGroupsSubscription = serverGroupsStream.merge(instancesStream).subscribe(this.onServerGroupsChanged);
    this.stateChangeSubscription = ReactInjector.$uiRouter.globals.success$.subscribe(this.onStateChanged);
    this.onStateChanged();
  }

  public componentWillUnmount() {
    this.stateChangeSubscription.unsubscribe();
    this.serverGroupsSubscription.unsubscribe();
  }

  public componentWillReceiveProps(nextProps: IServerGroupProps) {
    this.setState(this.getState(nextProps));
  }

  public loadDetails(event: React.MouseEvent<any>): void {
    event.persist();

    setTimeout(() => {
      if (event.isDefaultPrevented() || event.nativeEvent.defaultPrevented) {
        return;
      }
      ClusterState.multiselectModel.toggleServerGroup(this.props.serverGroup);
      event.preventDefault();
    });
  }

  private handleServerGroupClicked = (event: React.MouseEvent<any>) => {
    ReactGA.event({ category: 'Cluster Pod', action: 'Load Server Group Details' });
    this.loadDetails(event);
  };

  public render() {
    const { instances, images, jenkins, docker, isSelected, isMultiSelected } = this.state;
    const { serverGroup, application, sortFilter, hasDiscovery, hasLoadBalancers, hasManager } = this.props;
    const { account, region, name } = serverGroup;
    const { showAllInstances, listInstances } = sortFilter;
    const key = ScrollToService.toDomId(['serverGroup', account, region, name].join('-'));

    const serverGroupClassName = classNames({
      'server-group': true,
      'rollup-pod-server-group': true,
      clickable: true,
      'clickable-row': true,
      disabled: serverGroup.isDisabled,
      active: isSelected,
      managed: hasManager,
    });

    return (
      <div id={key} className={serverGroupClassName} onClick={this.handleServerGroupClicked}>
        <div className="cluster-container">
          <ServerGroupHeader
            application={application}
            images={images}
            isMultiSelected={isMultiSelected}
            jenkins={jenkins}
            serverGroup={serverGroup}
            sortFilter={sortFilter}
            docker={docker}
          />

          {showAllInstances && (
            <div className="instance-list">
              {listInstances ? (
                <div>
                  <InstanceList
                    hasDiscovery={hasDiscovery}
                    hasLoadBalancers={hasLoadBalancers}
                    instances={instances}
                    serverGroup={serverGroup}
                    sortFilter={sortFilter}
                  />
                </div>
              ) : (
                <div>
                  <Instances highlight={sortFilter.filter} instances={instances} />
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    );
  }
}
