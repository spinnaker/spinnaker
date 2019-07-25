import { Registry } from 'core/registry';

import { SlackNotificationType } from './SlackNotificationType';

Registry.pipeline.registerNotification({
  component: SlackNotificationType,
  key: 'slack',
  label: 'Slack',
});
