import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';

import { INotificationCategory } from './notificationCategories';
import { INotification } from './NotificationsPopover';

export interface INotificationCategoryProps {
  category: INotificationCategory;
  notifications: INotification[];
  isSelected: boolean;
  onCategorySelected(category: INotificationCategory): void;
}

/**
 * a click-to-select alert category
 * +--------------------------------+
 * | (icon) Category      (# badge) |
 * +--------------------------------+
 */
@autoBindMethods
export class NotificationCategory extends React.Component<INotificationCategoryProps, any> {
  public selectCategory(): void {
    this.props.onCategorySelected(this.props.category);
  }

  public render() {
    const { category, notifications } = this.props;

    const categoryClass =
      `clickable list-group-item flex-container-h baseline alerts-severity-${category.severity} ` +
      (this.props.isSelected ? 'selected ' : ' ');

    return (
      <li className={categoryClass} onClick={this.selectCategory} key={category.id}>
        <i className={`notification-category-icon fa ${category.icon}`}/>
        <span className="notification-category-label flex-grow">{category.label}</span>
        <span className="badge">{notifications.length}</span>
      </li>
    );
  }
}
