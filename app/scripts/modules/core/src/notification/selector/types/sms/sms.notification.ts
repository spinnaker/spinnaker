import { INotificationTypeConfig } from 'core/domain';
import { SmsNotificationType } from './SmsNotificationType';

export const smsNotification: INotificationTypeConfig = {
  component: SmsNotificationType,
  key: 'sms',
  label: 'SMS',
};
