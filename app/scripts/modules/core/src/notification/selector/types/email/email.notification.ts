import { INotificationTypeConfig } from 'core/domain';

import { EmailNotificationType } from './EmailNotificationType';

export const emailNotification: INotificationTypeConfig = {
  component: EmailNotificationType,
  key: 'email',
  label: 'Email',
};
