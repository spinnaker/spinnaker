import { PubsubNotificationType } from './PubsubNotificationType';
import { INotificationTypeConfig } from '../../../../domain';

export const pubsubNotification: INotificationTypeConfig = {
  component: PubsubNotificationType,
  key: 'pubsub',
  label: 'Pubsub',
};
