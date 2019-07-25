import { Registry } from 'core/registry';

import { PubsubNotificationType } from './PubsubNotificationType';

Registry.pipeline.registerNotification({
  component: PubsubNotificationType,
  key: 'pubsub',
  label: 'Pubsub',
});
