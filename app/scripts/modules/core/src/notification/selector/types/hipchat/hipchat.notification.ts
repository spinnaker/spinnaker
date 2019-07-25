import { Registry } from 'core/registry';

import { HipchatNotificationType } from './HipchatNotificationType';

Registry.pipeline.registerNotification({
  component: HipchatNotificationType,
  key: 'hipchat',
  label: 'HipChat',
});
