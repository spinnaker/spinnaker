import * as React from 'react';
import { BindAll } from 'lodash-decorators';
import { Subscription } from 'rxjs';

import { Application } from 'core/application';
import { ApplicationIcon, IApplicationIconProps } from './ApplicationIcon';
import { NgReact, ReactInjector } from 'core/reactShims';
import { Refresher } from 'core/presentation/refresher/Refresher';
import { Tooltip } from 'core/presentation/Tooltip';
import { UIView } from '@uirouter/react';
import { DebugWindow } from 'core/utils/consoleDebug';

import './application.less';

export interface IApplicationComponentProps {
  app: Application;
}

export interface IApplicationComponentState {
  compactHeader: boolean;
  refreshing: boolean;
  lastRefresh: number;
}

@BindAll()
export class ApplicationComponent extends React.Component<IApplicationComponentProps, IApplicationComponentState> {
  private activeStateRefreshUnsubscribe: () => void;
  private activeStateChangeSubscription: Subscription;
  private stopListeningToAppRefresh: Function;

  constructor(props: IApplicationComponentProps) {
    super(props);
    const { app } = props;
    this.state = this.parseState(props);
    if (props.app.notFound) {
      ReactInjector.recentHistoryService.removeLastItem('applications');
      return;
    }

    DebugWindow.application = app;
    app.enableAutoRefresh();
    this.activeStateChangeSubscription = app.activeStateChangeStream.subscribe(() => {
      this.resetActiveStateRefreshStream(this.props);
      this.setState(this.parseState(props));
    });
    this.stopListeningToAppRefresh = app.onRefresh(null, () => this.setState(this.parseState(props)));
  }

  private resetActiveStateRefreshStream(props: IApplicationComponentProps): void {
    if (this.activeStateRefreshUnsubscribe) { this.activeStateRefreshUnsubscribe(); }
    const activeState = props.app.activeState || props.app;
    this.activeStateRefreshUnsubscribe = activeState.onRefresh(null, () => {
      this.setState(this.parseState(props));
    });
  }

  private parseState(props: IApplicationComponentProps): IApplicationComponentState {
    const activeState = props.app.activeState || props.app;
    return {
      compactHeader: props.app.name.length > 20,
      lastRefresh: activeState.lastRefresh,
      refreshing: activeState.loading
    };
  }

  public componentWillUnmount(): void {
    if (!this.props.app.notFound) {
      DebugWindow.application = undefined;
      this.props.app.disableAutoRefresh();
    }
    if (this.activeStateChangeSubscription) {
      this.activeStateChangeSubscription.unsubscribe();
    }
    if (this.stopListeningToAppRefresh) {
      this.stopListeningToAppRefresh();
    }
  }

  public pageApplicationOwner(): void {
    ReactInjector.pagerDutyWriter.pageApplicationOwnerModal(this.props.app);
  }

  public handleRefresh(): void {
    // Force set refreshing to true since we are forcing the refresh
    this.setState({ refreshing: true });
    this.props.app.refresh(true);
  }

  public render() {
    // Get overridden application icon renderer
    const Icon: React.ComponentClass<IApplicationComponentProps> = ReactInjector.overrideRegistry.getComponent<IApplicationIconProps>('applicationIcon') || ApplicationIcon;

    const { ApplicationNav, ApplicationNavSecondary } = NgReact;

    const NotFound = this.props.app.notFound ? (
      <div>
        <h2 className="text-center">Application Not Found</h2>
        <p className="text-center" style={{ marginBottom: '20px' }}>Please check your URL - we can't find any data for <em>{this.props.app.name}</em>.</p>
      </div>
    ) : null;

    const Found = !this.props.app.notFound ? (
      <h2>
        <Icon app={this.props.app} />
        <span className="application-name">{this.props.app.name}</span>
        <Refresher
          refreshing={this.state.refreshing}
          lastRefresh={this.state.lastRefresh}
          refresh={this.handleRefresh}
        />
      </h2>
    ) : null;

    const PagerDutyButton = this.props.app.attributes.pdApiKey ? (
      <div className="page-button">
        <Tooltip value="Page application owner">
          <button
            className="btn btn-xs btn-danger btn-page-owner"
            onClick={this.pageApplicationOwner}
          >
            <i className="fa fa-phone"/>
          </button>
        </Tooltip>
      </div>
    ) : null;

    return (
      <div className="application">
        <div className={`page-header ${this.state.compactHeader ? 'compact-header' : ''}`}>
          {NotFound}
          <div className="container application-header">
            {Found}
            <div className="application-navigation">
              <ApplicationNav application={this.props.app}/>
              <div className="header-right">
                <ApplicationNavSecondary application={this.props.app}/>
                {PagerDutyButton}
              </div>
            </div>
          </div>
        </div>
        <div className="container scrollable-columns">
          <UIView className="secondary-panel" name="insight"/>
        </div>
      </div>
    );
  }
}
