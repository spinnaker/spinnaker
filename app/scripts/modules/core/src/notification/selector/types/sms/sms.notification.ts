import { Registry } from 'core/registry';

import { SmsNotificationType } from './SmsNotificationType';

Registry.pipeline.registerNotification({
  component: SmsNotificationType,
  key: 'sms',
  label: 'SMS',
});
