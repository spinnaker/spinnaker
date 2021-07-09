import { get } from 'lodash';
import React from 'react';

import { IDockerViewModel, IJenkinsViewModel } from './ServerGroup';
import { Application } from '../application';
import { ArtifactIconService } from '../artifact';
import { CloudProviderLogo } from '../cloudProvider';
import { IServerGroup } from '../domain';
import { EntityNotifications } from '../entityTag/notifications/EntityNotifications';
import { ISortFilter } from '../filterModel';
import { HealthCounts } from '../healthCounts';
import { LoadBalancersTagWrapper } from '../loadBalancer';
import { NameUtils } from '../naming';
import { Overridable } from '../overrideRegistry';
import { RunningTasksTag } from './pod/RunningTasksTag';

export interface IServerGroupHeaderProps {
  application: Application;
  images?: string[];
  isMultiSelected: boolean;
  jenkins: IJenkinsViewModel;
  docker: IDockerViewModel;
  serverGroup: IServerGroup;
  sortFilter: ISortFilter;
}

export class LoadBalancers extends React.Component<IServerGroupHeaderProps> {
  public render() {
    const { application, serverGroup } = this.props;
    const hasLoadBalancer = !!get(serverGroup, 'loadBalancers.length') || !!get(serverGroup, 'targetGroups.length');
    return (
      hasLoadBalancer && <LoadBalancersTagWrapper key="lbwrapper" application={application} serverGroup={serverGroup} />
    );
  }
}

export class MultiSelectCheckbox extends React.Component<IServerGroupHeaderProps> {
  public render() {
    // ServerGroup.tsx handles multi-select events and state
    const {
      isMultiSelected,
      sortFilter: { multiselect },
    } = this.props;
    return multiselect && <input type="checkbox" checked={isMultiSelected} />;
  }
}

export class CloudProviderIcon extends React.Component<IServerGroupHeaderProps> {
  public render() {
    const { serverGroup } = this.props;
    return <CloudProviderLogo provider={serverGroup.type} height="16px" width="16px" />;
  }
}

export interface IImageListState {
  collapsed: boolean;
}

export class ImageList extends React.Component<IServerGroupHeaderProps, IImageListState> {
  private toggle() {
    this.setState((previousState: IImageListState) => {
      return { collapsed: !previousState.collapsed };
    });
  }

  constructor(props: IServerGroupHeaderProps) {
    super(props);
    this.state = {
      collapsed: true,
    };
    this.toggle = this.toggle.bind(this);
  }

  public render() {
    const images = this.props.images.sort();
    const { collapsed } = this.state;
    const buttonStyle = {
      padding: 0,
      fontSize: '13px',
    };

    return (
      <>
        {collapsed && (
          <>
            <span>{images[0]}</span>
            &nbsp;
            {images.length > 1 && (
              <button className="link" onClick={this.toggle} style={buttonStyle}>
                (+ {images.length - 1} more)
              </button>
            )}
          </>
        )}
        {!collapsed && (
          <>
            {images.map((image, index) => (
              <span key={image}>
                {index > 0 && <br />}
                {image}
                {index < images.length - 1 ? ',' : ''}
              </span>
            ))}
            <br />
            <button className="link" onClick={this.toggle} style={buttonStyle}>
              collapse
            </button>
          </>
        )}
      </>
    );
  }
}

export class SequenceAndBuildAndImages extends React.Component<IServerGroupHeaderProps> {
  public render() {
    const { serverGroup, jenkins, images, docker } = this.props;
    const serverGroupSequence = NameUtils.getSequence(serverGroup.moniker.sequence);
    const ciBuild = serverGroup.buildInfo && serverGroup.buildInfo.ciBuild;
    const appArtifact = serverGroup.buildInfo && serverGroup.buildInfo.appArtifact;
    return (
      <div>
        {!!serverGroupSequence && <span className="server-group-sequence"> {serverGroupSequence}</span>}
        {!!serverGroupSequence && (!!jenkins || !!images) && <span>: </span>}
        {!!jenkins && (
          <a className="build-link sp-margin-xs-right" href={jenkins.href} target="_blank">
            Build: #{jenkins.number}
          </a>
        )}
        {!!docker && (
          <a className="build-link" href={docker.href} target="_blank">
            {docker.image}:{docker.tag || docker.digest}
          </a>
        )}

        {!!appArtifact && !!appArtifact.version ? (
          <>
            &nbsp;&nbsp;&nbsp;&nbsp;
            <img className="artifact-icon" src={ArtifactIconService.getPath('maven/file')} width="18" height="18" />
            {!!appArtifact.url ? (
              <a className="build-link" href={appArtifact.url} target="_blank">
                {appArtifact.version}
              </a>
            ) : (
              <>{appArtifact.version}</>
            )}
          </>
        ) : (
          !!ciBuild &&
          !!ciBuild.jobNumber && (
            <>
              &nbsp;&nbsp;&nbsp;&nbsp;
              <img className="artifact-icon" src={ArtifactIconService.getPath('jenkins/file')} width="18" height="18" />
              {!!ciBuild.jobUrl ? (
                <a className="build-link" href={ciBuild.jobUrl} target="_blank">
                  {ciBuild.jobNumber}
                </a>
              ) : (
                <>{ciBuild.jobNumber}</>
              )}
            </>
          )
        )}
        {!!images && <ImageList {...this.props} />}
      </div>
    );
  }
}

export class Alerts extends React.Component<IServerGroupHeaderProps> {
  public render() {
    const { application, serverGroup } = this.props;
    return (
      <EntityNotifications
        application={application}
        entity={serverGroup}
        entityType="serverGroup"
        hOffsetPercent="20%"
        onUpdate={() => application.serverGroups.refresh()}
        pageLocation="pod"
        placement="top"
      />
    );
  }
}

@Overridable('serverGroups.pod.header.health')
export class Health extends React.Component<IServerGroupHeaderProps> {
  public render() {
    const { serverGroup } = this.props;
    return <HealthCounts container={serverGroup.instanceCounts} />;
  }
}

export class RunningTasks extends React.Component<IServerGroupHeaderProps> {
  public render() {
    const { serverGroup } = this.props;
    const hasRunningExecutions = !!serverGroup.runningExecutions.length || !!serverGroup.runningTasks.length;

    return (
      hasRunningExecutions && (
        <RunningTasksTag tasks={serverGroup.runningTasks} executions={serverGroup.runningExecutions} />
      )
    );
  }
}

@Overridable('serverGroups.pod.header')
export class ServerGroupHeader extends React.Component<IServerGroupHeaderProps> {
  public render() {
    const props = this.props;

    return (
      <div className="horizontal top server-group-title sticky-header-3">
        <div className="horizontal section-title flex-1">
          <MultiSelectCheckbox {...props} />
          <CloudProviderIcon {...props} />
          <SequenceAndBuildAndImages {...props} />
          <Alerts {...props} />
        </div>

        <div className="horizontal center flex-none">
          <RunningTasks {...props} />
          <LoadBalancers {...props} />
          <Health {...props} />
        </div>
      </div>
    );
  }
}
