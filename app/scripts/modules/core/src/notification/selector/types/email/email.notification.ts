import { Registry } from 'core/registry';

import { EmailNotificationType } from './EmailNotificationType';

Registry.pipeline.registerNotification({
  component: EmailNotificationType,
  key: 'email',
  label: 'Email',
});
