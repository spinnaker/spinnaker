import { module } from 'angular';
import { react2angular } from 'react2angular';

import { NotificationsList } from './NotificationsList';
import { withErrorBoundary } from '../presentation/SpinErrorBoundary';

export const NOTIFICATION_LIST = 'spinnaker.core.notifications.notificationList';
module(NOTIFICATION_LIST, []).component(
  'notificationList',
  react2angular(withErrorBoundary(NotificationsList, 'notificationList'), [
    'application',
    'level',
    'stageType',
    'sendNotifications',
    'handleSendNotificationsChanged',
    'notifications',
    'updateNotifications',
  ]),
);
