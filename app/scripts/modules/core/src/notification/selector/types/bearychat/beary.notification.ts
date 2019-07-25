import { Registry } from 'core/registry';

import { BearychatNoficationType } from './BearychatNotificationType';

Registry.pipeline.registerNotification({
  component: BearychatNoficationType,
  key: 'bearychat',
  label: 'Bearychat',
});
