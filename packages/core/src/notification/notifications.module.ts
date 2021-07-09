import { module } from 'angular';

import { NotificationService } from './NotificationService';
import { extensionNotificationConfig } from './extensionNotificationConfig';
import './notification.types';
import { NOTIFICATION_LIST } from './notificationList.module';
import { Registry } from '../registry';

export const CORE_NOTIFICATION_NOTIFICATIONS_MODULE = 'spinnaker.core.notifications';
export const name = CORE_NOTIFICATION_NOTIFICATIONS_MODULE; // for backwards compatibility
module(CORE_NOTIFICATION_NOTIFICATIONS_MODULE, [NOTIFICATION_LIST]).run(() => {
  NotificationService.getNotificationTypeMetadata().then((types) => {
    types
      .filter((t) => t.uiType === 'BASIC')
      .forEach((t) => {
        Registry.pipeline.registerNotification({
          config: {},
          component: extensionNotificationConfig(t.parameters),
          key: t.notificationType,
          label: t.notificationType,
        });
      });
  });
});
