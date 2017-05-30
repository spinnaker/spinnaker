import * as React from 'react';
import * as marked from 'marked';
import { groupBy } from 'lodash';
import autoBindMethods from 'class-autobind-decorator';

import { EntityName } from './EntityName';
import { INotification } from './NotificationsPopover';

export interface IMessageNotifications {
  message: string;
  notifications: INotification[];
}

export interface IGroupedNotificationListProps {
  notifications: INotification[];
}

export interface IGroupedNotificationListState {
  alertsByMessage: IMessageNotifications[];
  collapsed: boolean;
}

/**
 * Notifications list where duplicated messages are grouped together
 * A clickable link shows/hides the entities (server groups) affected
 */
@autoBindMethods
export class GroupedNotificationList extends React.Component<IGroupedNotificationListProps, IGroupedNotificationListState> {
  public static defaultProps: Partial<IGroupedNotificationListProps> = {
    notifications: [],
  };

  constructor(props: IGroupedNotificationListProps) {
    super(props);
    this.state = {
      collapsed: true,
      alertsByMessage: this.getAlertsByMessage(props.notifications)
    };
  }

  public componentWillReceiveProps(newProps: IGroupedNotificationListProps): void {
    this.setState({ alertsByMessage: this.getAlertsByMessage(newProps.notifications) });
  }

  private getAlertsByMessage(notifications: INotification[]): IMessageNotifications[] {
    const grouped = groupBy(notifications, 'entityTag.value.message');

    return Object.keys(grouped).map(message => ({
      message,
      notifications: grouped[message],
    }));
  }

  public render() {
    return (
      <div className="notification-list">
        {this.state.alertsByMessage.map(alertsForMessage => (
          <AlertsForMessage key={alertsForMessage.message} alertsForMessage={alertsForMessage} />
        ))}
      </div>
    );
  }
}

interface IAlertsForMessageProps {
  alertsForMessage: IMessageNotifications;
}

interface IAlertsForMessageState {
  collapsed: boolean;
}

@autoBindMethods
class AlertsForMessage extends React.Component<IAlertsForMessageProps, IAlertsForMessageState> {
  public state: IAlertsForMessageState = {
    collapsed: true,
  };

  private renderServerGroupNames(notifications: INotification[]): JSX.Element {
    if (notifications.length === 1) {
      return this.renderServerGroupName(notifications[0]);
    }

    return (
      <div>
        <a className="clickable" onClick={this.toggleCollapsed}>
          <strong>
            <span> {this.state.collapsed ? 'Show' : 'Hide'} {notifications.length} server groups</span>
          </strong>
        </a>

        <div className="server-group-list">
          {!this.state.collapsed && notifications.map(this.renderServerGroupName)}
        </div>
      </div>
    )
  }

  private toggleCollapsed(): void {
    this.setState({ collapsed: !this.state.collapsed });
  }

  private renderServerGroupName(notification: INotification): JSX.Element {
    const { entityTags } = notification;
    const { entityRef: { region, entityId }} = entityTags;
    const alertKey = `${region}+${entityId}`;
    return <EntityName key={alertKey} tag={entityTags}/>;
  }

  public render() {
    const { alertsForMessage } = this.props;
    return (
      <div className="notification-message">
        {this.renderServerGroupNames(alertsForMessage.notifications)}
        <div dangerouslySetInnerHTML={{ __html: marked(alertsForMessage.message) }}/>
      </div>
    );
  }
}
