import { BearychatNotificationType } from './BearychatNotificationType';
import type { INotificationTypeConfig } from '../../../../domain';

export const bearyChatNotification: INotificationTypeConfig = {
  component: BearychatNotificationType,
  key: 'bearychat',
  label: 'Bearychat',
};
