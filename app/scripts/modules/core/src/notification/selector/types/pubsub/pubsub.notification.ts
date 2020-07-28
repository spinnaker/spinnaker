import { INotificationTypeConfig } from 'core/domain';

import { PubsubNotificationType } from './PubsubNotificationType';

export const pubsubNotification: INotificationTypeConfig = {
  component: PubsubNotificationType,
  key: 'pubsub',
  label: 'Pubsub',
};
