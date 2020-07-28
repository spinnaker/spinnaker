import { INotificationTypeConfig } from 'core/domain';

import { BearychatNotificationType } from './BearychatNotificationType';

export const bearyChatNotification: INotificationTypeConfig = {
  component: BearychatNotificationType,
  key: 'bearychat',
  label: 'Bearychat',
};
