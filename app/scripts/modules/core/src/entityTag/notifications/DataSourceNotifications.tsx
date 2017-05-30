import { module } from 'angular';

import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';
import { react2angular } from 'react2angular';

import { IEntityTags, IEntityTag } from 'core/domain';
import { Application } from 'core/application';
import { NotificationsPopover } from './NotificationsPopover';

export interface IDataSourceNotificationsProps {
  tags: IEntityTags[];
  application: Application;

  tabName: string;
}

/**
 * A notifications popover which shows rolled-up alert notifications.
 * Shown in the tabs for the main "data source entities" such as clusters and load balancers.
 * Identical alerts for multiple entities are grouped together.
 */
@autoBindMethods
export class DataSourceNotifications extends React.Component<IDataSourceNotificationsProps, void> {
  public getDataSourceAnalyticsLabel(): string {
    const { tabName, application, tags } = this.props;
    const alertsStr = tags.map(tag => tag.alerts.map((alert: IEntityTag) => alert.name).join(','));
    return [ tabName, application.name, alertsStr ].join(':');
  }

  public render() {
    const { application, tags } = this.props;

    return (
      <NotificationsPopover
        tags={tags}
        application={application}
        type="alerts"
        gaLabelFn={this.getDataSourceAnalyticsLabel}
        grouped={true}
        categorized={true}
        placement="bottom"
      />
    );
  }
}


export const DATA_SOURCE_NOTIFICATIONS = 'spinnaker.core.entityTag.alerts.datasourcenotifications';
const ngmodule = module(DATA_SOURCE_NOTIFICATIONS, []);

ngmodule.component('dataSourceNotifications', react2angular(DataSourceNotifications, [ 'tags', 'application', 'tabName' ]));
