import { EmailNotificationType } from './EmailNotificationType';
import { INotificationTypeConfig } from '../../../../domain';

export const emailNotification: INotificationTypeConfig = {
  component: EmailNotificationType,
  key: 'email',
  label: 'Email',
};
