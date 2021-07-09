import { BearychatNotificationType } from './BearychatNotificationType';
import { INotificationTypeConfig } from '../../../../domain';

export const bearyChatNotification: INotificationTypeConfig = {
  component: BearychatNotificationType,
  key: 'bearychat',
  label: 'Bearychat',
};
