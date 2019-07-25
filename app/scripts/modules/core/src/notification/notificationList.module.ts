import { module } from 'angular';
import { react2angular } from 'react2angular';

import { NotificationList } from './NotificationList';

export const NOTIFICATION_LIST = 'spinnaker.core.notifications.notificationList';
module(NOTIFICATION_LIST, []).component(
  'notificationList',
  react2angular(NotificationList, [
    'application',
    'level',
    'stageType',
    'sendNotifications',
    'handleSendNotificationsChanged',
    'notifications',
    'updateNotifications',
  ]),
);
