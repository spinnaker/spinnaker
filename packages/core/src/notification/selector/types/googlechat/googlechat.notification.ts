import { GooglechatNotificationType } from './GooglechatNotificationType';
import type { INotificationTypeConfig } from '../../../../domain';

export const googlechatNotification: INotificationTypeConfig = {
  component: GooglechatNotificationType,
  key: 'googlechat',
  label: 'Google Chat',
};
