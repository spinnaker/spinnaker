import { UISref } from '@uirouter/react';
import React from 'react';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { RunningTasks } from './RunningTasks';
import { IServerGroupDetailsProps, IServerGroupDetailsState } from './ServerGroupDetailsWrapper';
import { ServerGroupInsightActions } from './ServerGroupInsightActions';
import { CloudProviderLogo } from '../../cloudProvider/CloudProviderLogo';
import { SETTINGS } from '../../config/settings';
import { IServerGroup } from '../../domain';
import { EntityNotifications } from '../../entityTag/notifications/EntityNotifications';
import { ManagedResourceDetailsIndicator } from '../../managed';
import { ReactInjector } from '../../reactShims';
import { timestamp } from '../../utils/timeFormatters';
import { Spinner } from '../../widgets/spinners/Spinner';

export class ServerGroupDetails extends React.Component<IServerGroupDetailsProps, IServerGroupDetailsState> {
  private destroy$ = new Subject();
  private serverGroupsRefreshUnsubscribe: () => void;

  constructor(props: IServerGroupDetailsProps) {
    super(props);

    this.state = {
      loading: true,
      serverGroup: undefined,
    };
  }

  private autoClose(): void {
    ReactInjector.$state.params.allowModalToStayOpen = true;
    ReactInjector.$state.go('^', null, { location: 'replace' });
  }

  private updateServerGroup = (serverGroup: IServerGroup): void => {
    this.setState({ serverGroup, loading: false });
  };

  public componentDidMount(): void {
    this.props
      .detailsGetter(this.props, this.autoClose)
      .pipe(takeUntil(this.destroy$))
      .subscribe(this.updateServerGroup);
    this.serverGroupsRefreshUnsubscribe = this.props.app.serverGroups.onRefresh(null, () => {
      this.props
        .detailsGetter(this.props, this.autoClose)
        .pipe(takeUntil(this.destroy$))
        .subscribe(this.updateServerGroup);
    });
  }

  public componentWillReceiveProps(nextProps: IServerGroupDetailsProps): void {
    if (nextProps.serverGroup !== this.props.serverGroup) {
      this.props
        .detailsGetter(nextProps, this.autoClose)
        .pipe(takeUntil(this.destroy$))
        .subscribe(this.updateServerGroup);
    }
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
    this.serverGroupsRefreshUnsubscribe();
  }

  public render() {
    const { Actions, app, sections } = this.props;
    const { loading, serverGroup } = this.state;

    const showEntityTags = SETTINGS.feature && SETTINGS.feature.entityTags;
    const hasInsightActions = serverGroup && serverGroup.insightActions && serverGroup.insightActions.length > 0;

    const CloseButton = (
      <div className="close-button">
        <UISref to="^">
          <span className="glyphicon glyphicon-remove" />
        </UISref>
      </div>
    );

    // TODO: Move most of this to a BaseServerGroupDetails.tsx component that you just pass the things (retrieveServerGroup function, sections, ServerGroupActions)
    return (
      <div className={`details-panel ${serverGroup && serverGroup.isDisabled ? 'disabled' : ''}`}>
        {loading && (
          <div className="header">
            {CloseButton}
            <div className="horizontal center middle">
              <Spinner size="small" />
            </div>
          </div>
        )}

        {!loading && (
          <div className="header">
            {CloseButton}
            <div className="header-text horizontal middle">
              <CloudProviderLogo provider={serverGroup.type} height="36px" width="36px" />
              <h3 className="horizontal middle space-between flex-1">
                {serverGroup.name}
                {showEntityTags && (
                  <EntityNotifications
                    entity={serverGroup}
                    application={app}
                    placement="bottom"
                    hOffsetPercent="90%"
                    entityType="serverGroup"
                    pageLocation="details"
                    onUpdate={() => app.serverGroups.refresh()}
                  />
                )}
              </h3>
            </div>
            <div>
              <div className={`actions ${hasInsightActions ? 'insights' : ''}`}>
                <Actions app={app} serverGroup={serverGroup} />
                <ServerGroupInsightActions serverGroup={serverGroup} />
              </div>
            </div>
          </div>
        )}
        {serverGroup && serverGroup.isDisabled && (
          <div className="band band-info">Disabled {timestamp(serverGroup.disabledDate)}</div>
        )}
        {!loading && serverGroup.isManaged && (
          <ManagedResourceDetailsIndicator resourceSummary={serverGroup.managedResourceSummary} application={app} />
        )}
        {!loading && (
          <div className="content">
            <RunningTasks serverGroup={serverGroup} application={app} />
            {sections.map((Section, index) => (
              <Section key={index} app={app} serverGroup={serverGroup} />
            ))}
          </div>
        )}
      </div>
    );
  }
}
