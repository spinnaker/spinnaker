import * as React from 'react';
import { Subscription } from 'rxjs';

import { Application, ApplicationDataSource } from 'core/application';
import { IEntityTags } from 'core/domain';
import { DataSourceNotifications } from 'core/entityTag/notifications/DataSourceNotifications';

import { NavIcon } from './NavIcon';

export interface IDataSourceEntryProps {
  application: Application;
  dataSource?: ApplicationDataSource;
  hideIcon?: boolean;
}

export interface IDataSourceEntryState {
  runningCount?: number;
  tags?: IEntityTags[];
}

export class DataSourceEntry extends React.Component<IDataSourceEntryProps, IDataSourceEntryState> {
  private runningCountSubscription: Subscription;
  private entityTagsSubscription: Subscription;

  constructor(props: IDataSourceEntryProps) {
    super(props);
    this.state = this.getState(props);
  }

  public componentDidMount() {
    this.configureSubscriptions(this.props);
  }

  public componentWillReceiveProps(nextProps: IDataSourceEntryProps) {
    this.setState(this.getState(nextProps));
    this.clearSubscriptions();
    this.configureSubscriptions(nextProps);
  }

  private configureSubscriptions(props: IDataSourceEntryProps) {
    const { dataSource, application } = props;
    if (dataSource.badge && application.getDataSource(dataSource.badge)) {
      const badgeSource = application.getDataSource(dataSource.badge);
      this.runningCountSubscription = badgeSource.refresh$.subscribe(() =>
        this.setState({ runningCount: badgeSource.data.length }),
      );
    }
    this.entityTagsSubscription = dataSource.refresh$.subscribe(() => this.setState({ tags: dataSource.alerts }));
  }

  private getState(props: IDataSourceEntryProps): IDataSourceEntryState {
    const { dataSource, application } = props;
    return {
      tags: dataSource.alerts || [],
      runningCount: dataSource && dataSource.badge ? application.getDataSource(dataSource.badge).data.length : 0,
    };
  }

  private clearSubscriptions(): void {
    this.runningCountSubscription && this.runningCountSubscription.unsubscribe();
    this.entityTagsSubscription.unsubscribe();
  }

  public componentWillUnmount() {
    this.clearSubscriptions();
  }

  public render() {
    const { dataSource, application, hideIcon } = this.props;
    const { tags, runningCount } = this.state;
    return (
      <>
        {!hideIcon && <NavIcon icon={dataSource.icon} />}
        {' ' + dataSource.label}
        {runningCount > 0 && <span className="badge badge-running-count">{runningCount}</span>}
        <DataSourceNotifications tags={tags} application={application} tabName={dataSource.label} />
      </>
    );
  }
}
