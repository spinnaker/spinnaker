import { SmsNotificationType } from './SmsNotificationType';
import { INotificationTypeConfig } from '../../../../domain';

export const smsNotification: INotificationTypeConfig = {
  component: SmsNotificationType,
  key: 'sms',
  label: 'SMS',
};
