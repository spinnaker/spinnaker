import { SmsNotificationType } from './SmsNotificationType';
import type { INotificationTypeConfig } from '../../../../domain';

export const smsNotification: INotificationTypeConfig = {
  component: SmsNotificationType,
  key: 'sms',
  label: 'SMS',
};
