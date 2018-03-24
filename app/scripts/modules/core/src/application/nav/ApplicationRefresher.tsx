import * as React from 'react';
import { BindAll } from 'lodash-decorators';
import { Subscription } from 'rxjs';

import { Application } from 'core/application';
import { Refresher } from 'core/presentation/refresher/Refresher';

export interface IApplicationRefresherProps {
  app: Application;
}

export interface IApplicationRefresherState {
  refreshing: boolean;
  lastRefresh: number;
}

@BindAll()
export class ApplicationRefresher extends React.Component<IApplicationRefresherProps, IApplicationRefresherState> {

  private activeStateRefreshUnsubscribe: () => void;
  private activeStateChangeSubscription: Subscription;
  private stopListeningToAppRefresh: Function;

  constructor(props: IApplicationRefresherProps) {
    super(props);
    this.configureApplicationEventListeners(props.app);
    this.state = Object.assign(
      this.parseRefreshState(props),
    );
  }

  private resetActiveStateRefreshStream(props: IApplicationRefresherProps): void {
    if (this.activeStateRefreshUnsubscribe) { this.activeStateRefreshUnsubscribe(); }
    const activeState = props.app.activeState || props.app;
    this.activeStateRefreshUnsubscribe = activeState.onRefresh(null, () => {
      this.setState(this.parseRefreshState(props));
    });
  }

  public componentWillReceiveProps(nextProps: IApplicationRefresherProps) {
    this.configureApplicationEventListeners(nextProps.app);
  }

  private configureApplicationEventListeners(app: Application): void {
    app.ready().then(() => this.setState(this.parseRefreshState(this.props)));
    this.clearApplicationListeners();
    this.activeStateChangeSubscription = app.activeStateChangeStream.subscribe(() => {
      this.resetActiveStateRefreshStream(this.props);
      this.setState(this.parseRefreshState(this.props));
    });
    this.stopListeningToAppRefresh = app.onRefresh(null, () => {
      this.setState(this.parseRefreshState(this.props));
    });
  }

  private clearApplicationListeners(): void {
    if (this.activeStateChangeSubscription) {
      this.activeStateChangeSubscription.unsubscribe();
    }
    if (this.stopListeningToAppRefresh) {
      this.stopListeningToAppRefresh();
    }
  }

  private parseRefreshState(props: IApplicationRefresherProps): IApplicationRefresherState {
    const activeState = props.app.activeState || props.app;
    return {
      lastRefresh: activeState.lastRefresh,
      refreshing: activeState.loading,
    };
  }

  public componentWillUnmount(): void {
    this.clearApplicationListeners();
  }

  public handleRefresh(): void {
    // Force set refreshing to true since we are forcing the refresh
    this.setState({ refreshing: true });
    this.props.app.refresh(true);
  }

  public render() {
    return (
      <Refresher
        refreshing={this.state.refreshing}
        lastRefresh={this.state.lastRefresh}
        refresh={this.handleRefresh}
      />
    );
  }
}
