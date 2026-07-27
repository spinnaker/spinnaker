import { NotificationService } from './NotificationService';
import { extensionNotificationConfig } from './extensionNotificationConfig';
import { BUILTIN_NOTIFICATION_KEYS } from './notification.types';
import { Registry } from '../registry';

const normalizedBuiltinNotificationKeys = new Set(
  Array.from(BUILTIN_NOTIFICATION_KEYS, (notificationType) => notificationType.toLowerCase()),
);

export async function registerDynamicNotificationTypes(): Promise<void> {
  const types = await NotificationService.getNotificationTypeMetadata();
  types
    .filter((t) => t.uiType === 'BASIC')
    .forEach((t) => {
      const normalizedNotificationType = t.notificationType.toLowerCase();
      if (
        normalizedBuiltinNotificationKeys.has(normalizedNotificationType) ||
        Registry.pipeline.getNotificationTypes().some(({ key }) => key === t.notificationType)
      ) {
        return;
      }

      Registry.pipeline.registerNotification({
        config: {},
        component: extensionNotificationConfig(t.parameters),
        key: t.notificationType,
        label: t.notificationType,
      });
    });
}

export async function initializeDynamicNotificationTypes(): Promise<void> {
  try {
    await registerDynamicNotificationTypes();
  } catch (error) {
    console.error('Failed to load notification type metadata', error);
  }
}
