import React from 'react';

import { INotification } from './NotificationsPopover';
import { Markdown } from '../../presentation';
import { relativeTime, timestamp } from '../../utils';

export interface INotificationListProps {
  notifications: INotification[];
  onEditTag(notification: INotification): void;
  onDeleteTag(notification: INotification): void;
}

/**
 * Renders a list of notifications.
 * Provides edit and delete buttons.
 */
export class NotificationList extends React.Component<INotificationListProps> {
  public render() {
    const { notifications, onEditTag, onDeleteTag } = this.props;
    return (
      <div className="notification-list">
        {notifications.map((notification: INotification, idx: number) => {
          const {
            entityTag: {
              lastModified,
              value: { title, message, tagline },
            },
          } = notification;

          return (
            <div className="notification-message" key={idx}>
              {title && (
                <div className="notification-title">
                  <Markdown message={title} />
                </div>
              )}
              <Markdown message={message} />

              <div className="notification-tagline flex-container-h baseline">
                <Markdown className="small" message={tagline} />

                <div className="small flex-grow" title={timestamp(lastModified)}>
                  {relativeTime(lastModified)}
                </div>

                <NotificationActions notification={notification} onEditTag={onEditTag} onDeleteTag={onDeleteTag} />
              </div>
            </div>
          );
        })}
      </div>
    );
  }
}

interface IActionsProps {
  notification: INotification;
  onEditTag(notification: INotification): void;
  onDeleteTag(notification: INotification): void;
}

class NotificationActions extends React.Component<IActionsProps> {
  private editTag = (): void => {
    this.props.onEditTag(this.props.notification);
  };

  private deleteTag = (): void => {
    this.props.onDeleteTag(this.props.notification);
  };

  public render() {
    return (
      <div className="flex-nogrow actions actions-popover" style={{ position: 'relative' }}>
        <a onClick={this.editTag}>
          <span className="glyphicon glyphicon-cog clickable" />
        </a>
        <a onClick={this.deleteTag}>
          <span className="glyphicon glyphicon-trash clickable" />
        </a>
      </div>
    );
  }
}
