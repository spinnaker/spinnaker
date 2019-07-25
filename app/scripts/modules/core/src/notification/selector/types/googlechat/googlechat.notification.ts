import { Registry } from 'core/registry';

import { GooglechatNotificationType } from './GooglechatNotificationType';

Registry.pipeline.registerNotification({
  component: GooglechatNotificationType,
  key: 'googlechat',
  label: 'GoogleChat',
});
