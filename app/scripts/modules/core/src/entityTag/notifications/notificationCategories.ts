import { Dictionary, keyBy } from 'lodash';

export interface INotificationCategory {
  id: string;
  severity: number;
  label: string;
  icon: string;
}

const NOTIFICATION_CATEGORIES = [
  {
    id: 'urgentissues',
    label: 'Urgent Issues',
    icon: 'fa-exclamation',
    severity: 2,
  },

  {
    id: 'deprecation',
    label: 'Deprecations',
    icon: 'fa-wrench',
    severity: 1,
  },

  {
    id: 'default',
    label: 'Uncategorized',
    icon: 'fa-exclamation-circle',
    severity: 0,
  },
];

const BY_NAME: Dictionary<INotificationCategory> = keyBy(NOTIFICATION_CATEGORIES, 'id');

export class NotificationCategories {
  private static defineCategory(id: string, label = id, severity = 0, icon = 'fa-exclamation-circle') {
    return (BY_NAME[id] = { id, label, severity, icon });
  }

  public static getCategory(categoryName: string): INotificationCategory {
    if (!categoryName) {
      return BY_NAME['default'];
    }

    // Temporarily coerce 'blacklist' to 'urgentissues'
    categoryName = categoryName === 'blacklist' ? 'urgentissues' : categoryName;

    return BY_NAME[categoryName] || NotificationCategories.defineCategory(categoryName);
  }
}
