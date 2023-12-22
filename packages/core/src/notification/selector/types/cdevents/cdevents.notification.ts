import { CDEventsNotificationType } from './CDEventsNotificationType';
import type { INotificationTypeConfig } from '../../../../domain';

export const cdEventsNotification: INotificationTypeConfig = {
  component: CDEventsNotificationType,
  key: 'cdevents',
  label: 'CDEvents',
};
