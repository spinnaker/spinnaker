import { UISref } from '@uirouter/react';
import React from 'react';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { RunningTasks } from './RunningTasks';
import type { IServerGroupDetailsProps, IServerGroupDetailsState } from './ServerGroupDetailsWrapper';
import { ServerGroupInsightActions } from './ServerGroupInsightActions';
import { CloudProviderLogo } from '../../cloudProvider/CloudProviderLogo';
import { SETTINGS } from '../../config/settings';
import type { IServerGroup } from '../../domain';
import { EntityNotifications } from '../../entityTag/notifications/EntityNotifications';
import { ManagedResourceDetailsIndicator } from '../../managed';
import type { IRouterInjectedProps } from '../../navigation/routerContext';
import { withRouter } from '../../navigation/routerContext';
import { timestamp } from '../../utils/timeFormatters';
import { Spinner } from '../../widgets/spinners/Spinner';

export class ServerGroupDetailsComponent extends React.Component<
  IServerGroupDetailsProps & IRouterInjectedProps,
  IServerGroupDetailsState
> {
  private destroy$ = new Subject();
  private detailsRequest$ = new Subject();
  private detailsRequestGeneration = 0;
  private serverGroupsRefreshUnsubscribe: () => void;

  constructor(props: IServerGroupDetailsProps & IRouterInjectedProps) {
    super(props);

    this.state = {
      loading: true,
      serverGroup: undefined,
    };
  }

  private loadServerGroup(
    props: IServerGroupDetailsProps & IRouterInjectedProps,
    clearCurrentServerGroup = false,
  ): void {
    const requestGeneration = ++this.detailsRequestGeneration;
    this.detailsRequest$.next();
    if (clearCurrentServerGroup) {
      this.setState({ loading: true, serverGroup: undefined });
    }
    props
      .detailsGetter(props, () => {
        if (requestGeneration === this.detailsRequestGeneration) {
          props.stateService.params.allowModalToStayOpen = true;
          props.stateService.go('^', null, { location: 'replace' });
        }
      })
      .pipe(takeUntil(this.detailsRequest$), takeUntil(this.destroy$))
      .subscribe((serverGroup: IServerGroup) => {
        if (requestGeneration === this.detailsRequestGeneration) {
          this.setState({ serverGroup, loading: false });
        }
      });
  }

  private serverGroupIdentityChanged(nextProps: IServerGroupDetailsProps): boolean {
    const current = this.props.serverGroup;
    const next = nextProps.serverGroup;
    return (
      current.name !== next.name ||
      current.accountId !== next.accountId ||
      current.provider !== next.provider ||
      current.region !== next.region
    );
  }

  public componentDidMount(): void {
    this.loadServerGroup(this.props);
    this.serverGroupsRefreshUnsubscribe = this.props.app.serverGroups.onRefresh(null, () => {
      this.loadServerGroup(this.props);
    });
  }

  public componentWillReceiveProps(nextProps: IServerGroupDetailsProps & IRouterInjectedProps): void {
    if (nextProps.detailsGetter !== this.props.detailsGetter || this.serverGroupIdentityChanged(nextProps)) {
      this.loadServerGroup(nextProps, true);
    }
  }

  public componentWillUnmount(): void {
    this.detailsRequestGeneration++;
    this.detailsRequest$.next();
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
                {serverGroup.displayName ? serverGroup.displayName : serverGroup.name}
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

export const ServerGroupDetails = withRouter(ServerGroupDetailsComponent);
ServerGroupDetails.displayName = 'ServerGroupDetails';
