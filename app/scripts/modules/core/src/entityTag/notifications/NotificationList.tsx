import * as React from 'react';
import * as marked from 'marked';
import * as DOMPurify from 'dompurify';
import autoBindMethods from 'class-autobind-decorator';

import { relativeTime, timestamp } from 'core/utils';
import { INotification } from './NotificationsPopover';

export interface INotificationListProps {
  notifications: INotification[];
  onEditTag(notification: INotification): void;
  onDeleteTag(notification: INotification): void;
}

/**
 * Renders a list of notifications.
 * Provides edit and delete buttons.
 */
export class NotificationList extends React.Component<INotificationListProps, void> {
  public render() {
    const { notifications, onEditTag, onDeleteTag } = this.props;

    return (
      <div className="notification-list">
        {notifications.map((notification: INotification, idx: number) => (
          <div className="notification-message" key={idx}>
            <div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(marked(notification.entityTag.value.message)) }}/>

            <div className="flex-container-h">
              <div className="small flex-grow" title={timestamp(notification.entityTags.lastModified)}>
                {relativeTime(notification.entityTags.lastModified)}
              </div>

              <NotificationActions notification={notification} onEditTag={onEditTag} onDeleteTag={onDeleteTag} />
            </div>
          </div>
        ))}
      </div>
    );
  }
}

interface IActionsProps {
  notification: INotification;
  onEditTag(notification: INotification): void;
  onDeleteTag(notification: INotification): void;
}

@autoBindMethods
class NotificationActions extends React.Component<IActionsProps, void> {
  private editTag(): void {
    this.props.onEditTag(this.props.notification);
  }

  private deleteTag(): void {
    this.props.onDeleteTag(this.props.notification);
  }

  public render() {
    return (
      <div className="flex-nogrow actions actions-popover" style={{position: 'relative'}}>
        <a onClick={this.editTag}><span className="glyphicon glyphicon-cog clickable" /></a>
        <a onClick={this.deleteTag}><span className="glyphicon glyphicon-trash clickable" /></a>
      </div>
    )
  }
}
