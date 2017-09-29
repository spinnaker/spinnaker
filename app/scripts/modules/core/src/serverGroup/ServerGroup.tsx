import * as React from 'react';
import * as ReactGA from 'react-ga';
import { has, get } from 'lodash';
import * as classNames from 'classnames';
import { BindAll } from 'lodash-decorators';
import { Subscription } from 'rxjs/Subscription';

import { Application } from 'core/application';
import { CloudProviderLogo } from 'core/cloudProvider';
import { serverGroupSequenceFilter } from 'core/cluster/serverGroup.sequence.filter';
import { IInstance, IServerGroup } from 'core/domain';
import { EntityNotifications } from 'core/entityTag/notifications/EntityNotifications';
import { HealthCounts } from 'core/healthCounts';
import { LoadBalancersTagWrapper } from 'core/loadBalancer';
import { NamingService } from 'core/naming';
import { NgReact, ReactInjector } from 'core/reactShims';
import { Instances } from 'core/instance/Instances';
import { ScrollToService } from 'core/utils';

export interface JenkinsViewModel {
  number: number;
  href?: string;
}

export interface IServerGroupProps {
  cluster: string;
  serverGroup: IServerGroup;
  application: Application;
  sortFilter: any;
  hasLoadBalancers: boolean;
  hasDiscovery: boolean;
}

export interface IServerGroupState {
  serverGroup: IServerGroup;
  serverGroupSequence: string;
  jenkins: JenkinsViewModel;
  hasBuildInfo: boolean;
  instances: IInstance[];
  images?: string;

  filter: string;
  showAllInstances: boolean;
  listInstances: boolean;

  multiselect: boolean;
  isSelected: boolean; // single select mode
  isMultiSelected: boolean; // multiselect mode
}

const getSequence = serverGroupSequenceFilter(new NamingService());

@BindAll()
export class ServerGroup extends React.Component<IServerGroupProps, IServerGroupState> {
  private stateChangeSubscription: Subscription;
  private serverGroupsSubscription: Subscription;

  constructor(props: IServerGroupProps) {
    super(props);
    this.state = this.getState(props);
  }

  private getState(props: IServerGroupProps): IServerGroupState {
    const { serverGroup } = props;
    const { showAllInstances, listInstances, multiselect, filter } = props.sortFilter;
    const instances = serverGroup.instances.filter(i => ReactInjector.clusterFilterService.shouldShowInstance(i));
    const serverGroupSequence = getSequence(serverGroup.name);
    const hasBuildInfo = !!serverGroup.buildInfo;
    const isSelected = this.isSelected(serverGroup);
    const isMultiSelected = this.isMultiSelected(multiselect, serverGroup);
    const jenkinsConfig = serverGroup.buildInfo && serverGroup.buildInfo.jenkins;

    let jenkins: JenkinsViewModel = null;
    let images: string = null;

    if (jenkinsConfig && (jenkinsConfig.host || jenkinsConfig.fullUrl || serverGroup.buildInfo.buildInfoUrl)) {
      const fromHost = jenkinsConfig.host && [jenkinsConfig.host + 'job', jenkinsConfig.name, jenkinsConfig.number, ''].join('/');
      const fromFullUrl = jenkinsConfig.fullUrl;
      const fromBuildInfo = serverGroup.buildInfo.buildInfoUrl;

      jenkins = {
        number: jenkinsConfig.number,
        href: fromBuildInfo || fromFullUrl || fromHost ,
      };
    } else if (has(serverGroup, 'buildInfo.images')) {
      images = serverGroup.buildInfo.images.join(', ');
    }

    return {
      serverGroup,
      serverGroupSequence,
      jenkins,
      hasBuildInfo,
      instances,
      images,
      filter,
      showAllInstances,
      listInstances,
      multiselect,
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
    return multiselect && ReactInjector.MultiselectModel.serverGroupIsSelected(serverGroup);
  }

  private onServerGroupsChanged() {
    const isMultiSelected = this.isMultiSelected(this.state.multiselect, this.state.serverGroup);
    this.setState({ isMultiSelected });
    // Enables the (angular) details pane to detect the changes
    ReactInjector.$rootScope.$applyAsync(() => false);
  }

  private onStateChanged() {
    this.setState({ isSelected: this.isSelected(this.state.serverGroup) });
  }

  public componentDidMount(): void {
    const { serverGroupsStream, instancesStream } = ReactInjector.MultiselectModel;

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

  public headerIsSticky(): boolean {
    const { showAllInstances, listInstances, instances } = this.state;
    if (!showAllInstances) {
      return false;
    }

    return listInstances ? instances.length > 1 : instances.length > 20;
  }

  public loadDetails(event: React.MouseEvent<any>): void {
    event.persist();

    setTimeout(() => {
      if (event.isDefaultPrevented() || event.nativeEvent.defaultPrevented) {
        return;
      }
      ReactInjector.MultiselectModel.toggleServerGroup(this.props.serverGroup);
      event.preventDefault();
    })
  };

  private handleServerGroupClicked(event: React.MouseEvent<any>) {
    ReactGA.event({ category: 'Cluster Pod', action: 'Load Server Group Details' });
    this.loadDetails(event);
  }

  public render() {
    const { InstanceList, RunningTasksTag } = NgReact;
    const { filter, instances, images, jenkins, isSelected, multiselect, isMultiSelected, showAllInstances, listInstances } = this.state;
    const { serverGroup, application, sortFilter, hasDiscovery, hasLoadBalancers } = this.props;
    const { account, region, name, type } = serverGroup;
    const key = ScrollToService.toDomId(['serverGroup', account, region, name].join('-'));

    const hasJenkins = !!jenkins;
    const hasImages = !!images;
    const hasRunningExecutions = !!serverGroup.runningExecutions.length || !!serverGroup.runningTasks.length;
    const hasLoadBalancer = !!get(serverGroup, 'loadBalancers.length') || !!get(serverGroup, 'targetGroups.length');

    const serverGroupClassName = classNames({
      'server-group': true,
      'rollup-pod-server-group': true,
      'clickable': true,
      'clickable-row': true,
      'disabled': serverGroup.isDisabled,
      'active': isSelected,
    });

    const headerClassName = classNames({
      'server-group-title': true,
      'sticky-header-3': this.headerIsSticky(),
    });

    const col1ClassName = `col-md-${images ? 9 : 8 } col-sm-6 section-title`;
    const col2ClassName = `col-md-${images ? 3 : 4 } col-sm-6 text-right`;

    return (
      <div key={key} id={key} className={serverGroupClassName} onClick={this.handleServerGroupClicked}>
        <div className="cluster-container">
          <div className={headerClassName}>
            <div className="container-fluid no-padding">
              <div className="row">
                <div className={col1ClassName}>
                  {multiselect && <input type="checkbox" checked={isMultiSelected}/>}

                  <CloudProviderLogo provider={type} height="16px" width="16px"/>

                  <span className="server-group-sequence"> {this.state.serverGroupSequence}</span>
                  {(hasJenkins || hasImages) && <span>: </span>}
                  {hasJenkins && <a href={jenkins.href} target="_blank">Build: #{jenkins.number}</a>}
                  {hasImages && <span>{images}</span>}

                  <EntityNotifications
                    entity={serverGroup}
                    application={application}
                    placement="top"
                    hOffsetPercent="20%"
                    entityType="serverGroup"
                    pageLocation="pod"
                    onUpdate={application.serverGroups.refresh}
                  />
                </div>

                <div className={col2ClassName}>
                  <HealthCounts container={serverGroup.instanceCounts}/>

                  {hasRunningExecutions && (
                    <RunningTasksTag
                      application={application}
                      tasks={serverGroup.runningTasks}
                      executions={serverGroup.runningExecutions}
                    />
                  )}

                  {hasLoadBalancer && <LoadBalancersTagWrapper application={application} serverGroup={serverGroup}/>}
                </div>
              </div>
            </div>
          </div>

          {showAllInstances && (
            <div className="instance-list">
              {listInstances ? (
                <div>
                  <InstanceList
                    serverGroup={serverGroup}
                    instances={instances}
                    sortFilter={sortFilter}
                    hasDiscovery={hasDiscovery}
                    hasLoadBalancers={hasLoadBalancers}
                  />
                </div>
              ) : (
                <div>
                  <Instances highlight={filter} instances={instances}/>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    )
  }
}
