import { PubsubNotificationType } from './PubsubNotificationType';
import type { INotificationTypeConfig } from '../../../../domain';

export const pubsubNotification: INotificationTypeConfig = {
  component: PubsubNotificationType,
  key: 'pubsub',
  label: 'Pubsub',
};
