import { GooglechatNotificationType } from './GooglechatNotificationType';
import { INotificationTypeConfig } from '../../../../domain';

export const googlechatNotification: INotificationTypeConfig = {
  component: GooglechatNotificationType,
  key: 'googlechat',
  label: 'GoogleChat',
};
