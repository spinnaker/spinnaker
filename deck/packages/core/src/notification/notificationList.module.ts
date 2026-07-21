import { module } from 'angular';

import { NotificationsList } from './NotificationsList';
import { angularComponentFromReact } from '../angular/angularComponentFromReact';

export const NOTIFICATION_LIST = 'spinnaker.core.notifications.notificationList';
module(NOTIFICATION_LIST, []).component(
  'notificationList',
  angularComponentFromReact(NotificationsList, 'notificationList', [
    'application',
    'level',
    'stageType',
    'sendNotifications',
    'handleSendNotificationsChanged',
    'notifications',
    'updateNotifications',
  ]),
);
