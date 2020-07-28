import { INotificationTypeConfig } from 'core/domain';

import { GooglechatNotificationType } from './GooglechatNotificationType';

export const googlechatNotification: INotificationTypeConfig = {
  component: GooglechatNotificationType,
  key: 'googlechat',
  label: 'GoogleChat',
};
