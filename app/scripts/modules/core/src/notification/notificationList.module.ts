import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';

import { NotificationsList } from './NotificationsList';

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
