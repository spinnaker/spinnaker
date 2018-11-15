import * as React from 'react';
import { Observable, Subject } from 'rxjs';

import { Application, ApplicationDataSource, IFetchStatus } from 'core/application';
import { Refresher } from 'core/presentation/refresher/Refresher';

export interface IApplicationRefresherProps {
  app: Application;
}

export interface IApplicationRefresherState {
  refreshing: boolean;
  lastRefresh: number;
}

export class ApplicationRefresher extends React.Component<IApplicationRefresherProps, IApplicationRefresherState> {
  private app$ = new Subject<Application>();
  private destroy$ = new Subject();

  public state: IApplicationRefresherState = {
    refreshing: false,
    lastRefresh: 0,
  };

  public componentDidMount() {
    this.app$
      .filter(app => !!app)
      .distinctUntilChanged()
      // follow the data source from the active tab
      .switchMap(app => app.activeDataSource$)
      .startWith(null)
      .mergeMap((dataSource: ApplicationDataSource) => {
        // If there is no active data source (e.g., on config tab), use the application's status.
        const fetchStatus$: Observable<IFetchStatus> = (dataSource && dataSource.status$) || this.props.app.status$;
        return fetchStatus$.filter(fetchStatus => ['FETCHING', 'FETCHED', 'ERROR'].includes(fetchStatus.status));
      })
      .takeUntil(this.destroy$)
      .subscribe(fetchStatus => this.update(fetchStatus));

    this.app$.next(this.props.app);
  }

  public componentWillReceiveProps(nextProps: IApplicationRefresherProps) {
    this.app$.next(nextProps.app);
  }

  public componentWillUnmount() {
    this.destroy$.next();
  }

  private update(fetchStatus: IFetchStatus): void {
    if (fetchStatus.status === 'FETCHING') {
      this.setState({ refreshing: true });
    } else if (fetchStatus.status === 'FETCHED') {
      this.setState({ refreshing: false, lastRefresh: fetchStatus.lastRefresh });
    } else {
      this.setState({ refreshing: false });
    }
  }

  private handleRefresh = (): void => {
    this.props.app.refresh(true);
  };

  public render() {
    return (
      <Refresher refreshing={this.state.refreshing} lastRefresh={this.state.lastRefresh} refresh={this.handleRefresh} />
    );
  }
}
